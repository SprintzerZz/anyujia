#!/usr/bin/env python3
"""Evaluate legal retrieval and grounded answer endpoints with versioned JSONL cases."""

from __future__ import annotations

import argparse
import collections
import datetime as dt
import hashlib
import json
import math
import re
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

SUCCESS_CODE = 200000
ANSWER_METRICS = ("citationValidity", "citationCoverage", "groundednessProxy",
                  "answerKeywordCoverage", "scopeCompliance")
RETRIEVAL_METRICS = ("recallAtK", "hitRateAtK", "mrr", "ndcgAtK")


def load_cases(path: Path, mode: str = "retrieval") -> list[dict[str, Any]]:
    cases: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        case = json.loads(raw)
        case_id = str(case.get("id", "")).strip()
        if not case_id or not case.get("query"):
            raise ValueError(f"line {line_number}: id and query are required")
        if case_id in seen_ids:
            raise ValueError(f"line {line_number}: duplicate id {case_id}")
        seen_ids.add(case_id)
        if mode == "retrieval" and case.get("expectedBehavior") == "out_of_scope" \
                and not (case.get("expectedChunkIds") or case.get("expectedSources")):
            continue
        if mode == "retrieval" and not (case.get("expectedChunkIds") or case.get("expectedSources")):
            raise ValueError(f"line {line_number}: expectedChunkIds or expectedSources are required")
        if mode == "answer":
            required = ("referenceAnswer", "requiredKeywords", "expectedBehavior", "expectedSources")
            missing = [field for field in required if field not in case]
            if missing:
                raise ValueError(f"line {line_number}: missing answer fields {', '.join(missing)}")
            if case["expectedBehavior"] not in {"answer", "insufficient_evidence", "out_of_scope"}:
                raise ValueError(f"line {line_number}: unsupported expectedBehavior")
            if case.get("reviewStatus") not in {"source_verified", "legal_reviewed"}:
                raise ValueError(f"line {line_number}: reviewStatus must record source verification")
        cases.append(case)
    if not cases:
        raise ValueError("dataset contains no evaluation cases")
    return cases


def post_json(endpoint: str, path: str, user_from: str, body: dict[str, Any], timeout: float) -> tuple[dict[str, Any], float]:
    request = urllib.request.Request(
        endpoint.rstrip("/") + path,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", "userFrom": user_from},
        method="POST",
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"request failed with HTTP {exc.code}: {detail}") from exc
    latency_ms = (time.perf_counter() - started) * 1000
    if payload.get("code") != SUCCESS_CODE:
        raise RuntimeError(f"endpoint returned application error: {payload}")
    return payload.get("data") or {}, latency_ms


def request_body(case: dict[str, Any]) -> dict[str, Any]:
    return {"query": case["query"], "regionCode": case.get("regionCode", "GLOBAL"),
            "asOfDate": case.get("asOfDate", ""), "limit": int(case.get("k", 5))}


def search(endpoint: str, user_from: str, case: dict[str, Any], timeout: float) -> tuple[list[dict[str, Any]], float]:
    data, latency_ms = post_json(endpoint, "/legal/knowledge/search", user_from, request_body(case), timeout)
    return data.get("passages") or [], latency_ms


def answer(endpoint: str, user_from: str, case: dict[str, Any], timeout: float) -> tuple[dict[str, Any], float]:
    return post_json(endpoint, "/legal/knowledge/answer", user_from, request_body(case), timeout)


def source_key(item: dict[str, Any]) -> tuple[str, str]:
    return str(item.get("title", "")).strip(), str(item.get("articleNumber", "")).strip()


def source_matches(actual: dict[str, Any], expected: dict[str, Any]) -> bool:
    actual_title, actual_article = source_key(actual)
    expected_title, expected_article = source_key(expected)
    title_matches = actual_title == expected_title or actual_title in expected_title or expected_title in actual_title
    return title_matches and actual_article == expected_article


def relevance_flags(case: dict[str, Any], passages: list[dict[str, Any]]) -> tuple[list[bool], int]:
    expected_ids = {str(value) for value in case.get("expectedChunkIds", [])}
    expected_sources = case.get("expectedSources", [])
    flags = [str(item.get("chunkId")) in expected_ids
             or any(source_matches(item, expected) for expected in expected_sources)
             for item in passages]
    return flags, len(expected_ids) + len(expected_sources)


def score_retrieval_case(case: dict[str, Any], passages: list[dict[str, Any]], latency_ms: float) -> dict[str, float]:
    flags, expected_count = relevance_flags(case, passages)
    hit_ranks = [index for index, relevant in enumerate(flags, 1) if relevant]
    recall = min(sum(flags), expected_count) / expected_count
    reciprocal_rank = 0.0 if not hit_ranks else 1.0 / hit_ranks[0]
    dcg = sum(1.0 / math.log2(index + 1) for index in hit_ranks)
    ideal_hits = min(expected_count, len(passages))
    ideal_dcg = sum(1.0 / math.log2(index + 1) for index in range(1, ideal_hits + 1))
    return {"recallAtK": recall, "hitRateAtK": 1.0 if hit_ranks else 0.0,
            "mrr": reciprocal_rank, "ndcgAtK": 0.0 if ideal_dcg == 0 else dcg / ideal_dcg,
            "latencyMillis": latency_ms}


def han_bigrams(text: str) -> set[str]:
    tokens: set[str] = set(re.findall(r"[A-Za-z0-9]{2,}", text.lower()))
    for run in re.findall(r"[\u4e00-\u9fff]+", text):
        tokens.update(run[index:index + 2] for index in range(max(0, len(run) - 1)))
    return tokens


def score_answer_case(case: dict[str, Any], data: dict[str, Any], latency_ms: float) -> dict[str, float]:
    response = str(data.get("answer", ""))
    citations = data.get("citations") or []
    valid_citations = [item for item in citations
                       if isinstance(item.get("index"), int) and item["index"] > 0
                       and item.get("chunkId") is not None and bool(str(item.get("quote", "")).strip())]
    citation_validity = 1.0 if len(valid_citations) == len(citations) else 0.0
    expected_sources = case.get("expectedSources", [])
    covered_sources = sum(any(source_matches(actual, expected) for actual in valid_citations)
                          for expected in expected_sources)
    citation_coverage = covered_sources / len(expected_sources) if expected_sources else 1.0
    answer_tokens = han_bigrams(response)
    context_tokens = han_bigrams("\n".join(str(item.get("quote", "")) for item in valid_citations))
    groundedness = len(answer_tokens & context_tokens) / len(answer_tokens) if answer_tokens else 0.0
    keywords = [str(value) for value in case.get("requiredKeywords", [])]
    keyword_coverage = sum(keyword in response for keyword in keywords) / len(keywords) if keywords else 1.0
    behavior = case.get("expectedBehavior", "answer")
    boundary_markers = ("不足", "无法", "不属于", "不能确定", "建议补充", "未检索到")
    scope_compliance = (1.0 if response and valid_citations else 0.0) if behavior == "answer" \
        else (1.0 if any(marker in response for marker in boundary_markers) else 0.0)
    return {"citationValidity": citation_validity, "citationCoverage": citation_coverage,
            "groundednessProxy": groundedness, "answerKeywordCoverage": keyword_coverage,
            "scopeCompliance": scope_compliance, "latencyMillis": latency_ms}


def percentile(values: list[float], percentile_value: float) -> float:
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * percentile_value
    lower, upper = math.floor(position), math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def summarize(results: list[dict[str, Any]], metric_names: tuple[str, ...]) -> dict[str, Any]:
    metrics = [item["metrics"] for item in results]
    latencies = [item["latencyMillis"] for item in metrics]
    summary = {"caseCount": len(results)}
    summary.update({name: statistics.fmean(item[name] for item in metrics) for name in metric_names})
    summary["latencyMillis"] = {"mean": statistics.fmean(latencies),
                                "p50": percentile(latencies, 0.50),
                                "p95": percentile(latencies, 0.95),
                                "p99": percentile(latencies, 0.99)}
    return summary


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def git_value(args: list[str]) -> str | None:
    try:
        return subprocess.check_output(
            ["git", *args],
            cwd=Path(__file__).resolve().parents[3],
            stderr=subprocess.DEVNULL,
            text=True,
            encoding="utf-8",
        ).strip()
    except Exception:
        return None


def run_metadata(args: argparse.Namespace, cases: list[dict[str, Any]]) -> dict[str, Any]:
    dataset_versions = collections.Counter(str(case.get("datasetVersion", "unknown")) for case in cases)
    review_states = collections.Counter(str(case.get("reviewStatus", "unknown")) for case in cases)
    categories = collections.Counter(str(case.get("category", "unknown")) for case in cases)
    status = git_value(["status", "--short"])
    return {
        "generatedAt": dt.datetime.now(dt.UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "runLabel": args.run_label,
        "gitSha": git_value(["rev-parse", "HEAD"]),
        "gitDirty": bool(status),
        "mode": args.mode,
        "endpoint": args.endpoint,
        "userFrom": args.user_from,
        "dataset": str(args.dataset),
        "datasetSha256": sha256_file(args.dataset),
        "evaluatorSha256": sha256_file(Path(__file__)),
        "caseCount": len(cases),
        "datasetVersions": dict(sorted(dataset_versions.items())),
        "reviewStates": dict(sorted(review_states.items())),
        "categories": dict(sorted(categories.items())),
    }


def failure_examples(results: list[dict[str, Any]], metric_names: tuple[str, ...],
                     max_examples: int = 8) -> list[dict[str, Any]]:
    examples: list[dict[str, Any]] = []
    for item in results:
        weak_metrics = {name: item["metrics"][name] for name in metric_names
                        if float(item["metrics"][name]) < 1.0}
        if not weak_metrics:
            continue
        examples.append({
            "id": item["id"],
            "query": item["query"],
            "weakMetrics": weak_metrics,
        })
        if len(examples) >= max_examples:
            break
    return examples


def baseline_diff(summary: dict[str, Any], baseline_path: Path, metric_names: tuple[str, ...]) -> dict[str, float]:
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    baseline_summary = baseline.get("summary", baseline)
    return {metric: summary[metric] - float(baseline_summary[metric])
            for metric in metric_names if metric in baseline_summary}


def ragas_record(case: dict[str, Any], data: dict[str, Any]) -> dict[str, Any]:
    return {"user_input": case["query"], "response": data.get("answer", ""),
            "retrieved_contexts": [item.get("quote", "") for item in data.get("citations") or []],
            "reference": case.get("referenceAnswer", "")}


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_jsonl(path: Path, values: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(item, ensure_ascii=False) + "\n" for item in values), encoding="utf-8")


def self_test() -> None:
    first = {"id": "hit", "query": "q", "expectedChunkIds": ["10", "20"]}
    second = {"id": "miss", "query": "q", "expectedChunkIds": ["40"]}
    perfect = score_retrieval_case(first, [{"chunkId": "10"}, {"chunkId": "30"}, {"chunkId": "20"}], 12.0)
    missed = score_retrieval_case(second, [{"chunkId": "10"}, {"chunkId": "20"}], 20.0)
    retrieval_summary = summarize([{"metrics": perfect}, {"metrics": missed}], RETRIEVAL_METRICS)
    answer_case = {"expectedSources": [{"title": "住房租赁条例", "articleNumber": "第十条"}],
                   "requiredKeywords": ["押金"], "expectedBehavior": "answer"}
    answer_data = {"answer": "押金不得无正当理由扣减", "citations": [{"index": 1, "chunkId": 10,
                   "title": "住房租赁条例", "articleNumber": "第十条", "quote": "不得无正当理由扣减押金"}]}
    answer_metrics = score_answer_case(answer_case, answer_data, 15.0)
    assert retrieval_summary["recallAtK"] == 0.5 and retrieval_summary["hitRateAtK"] == 0.5
    assert answer_metrics["citationValidity"] == 1.0 and answer_metrics["citationCoverage"] == 1.0
    assert answer_metrics["answerKeywordCoverage"] == 1.0
    print(json.dumps({"selfTest": "PASS", "retrieval": retrieval_summary,
                      "answer": answer_metrics}, ensure_ascii=False, indent=2))


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")

    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path)
    parser.add_argument("--mode", choices=("retrieval", "answer"), default="retrieval")
    parser.add_argument("--endpoint", default="http://127.0.0.1:18086")
    parser.add_argument("--user-from", default="service")
    parser.add_argument("--timeout", type=float, default=60.0)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--export-ragas", type=Path)
    parser.add_argument("--run-label", default="local")
    parser.add_argument("--validate-dataset", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if args.dataset is None:
        parser.error("--dataset is required unless --self-test is used")
    cases = load_cases(args.dataset, args.mode)
    if args.validate_dataset:
        print(json.dumps({"metadata": run_metadata(args, cases), "validation": "PASS"},
                         ensure_ascii=False, indent=2))
        return

    case_results: list[dict[str, Any]] = []
    ragas_records: list[dict[str, Any]] = []
    metric_names = RETRIEVAL_METRICS if args.mode == "retrieval" else ANSWER_METRICS
    for case in cases:
        if args.mode == "retrieval":
            passages, latency_ms = search(args.endpoint, args.user_from, case, args.timeout)
            metrics = score_retrieval_case(case, passages, latency_ms)
            case_results.append({"id": case["id"], "query": case["query"],
                                 "retrievedPassages": passages, "metrics": metrics})
        else:
            data, latency_ms = answer(args.endpoint, args.user_from, case, args.timeout)
            metrics = score_answer_case(case, data, latency_ms)
            case_results.append({"id": case["id"], "query": case["query"],
                                 "answer": data, "metrics": metrics})
            ragas_records.append(ragas_record(case, data))
    report: dict[str, Any] = {"metadata": run_metadata(args, cases),
                              "summary": summarize(case_results, metric_names),
                              "failureExamples": failure_examples(case_results, metric_names),
                              "cases": case_results}
    if args.baseline:
        report["baselineDiff"] = baseline_diff(report["summary"], args.baseline, metric_names)
    if args.export_ragas:
        if args.mode != "answer":
            parser.error("--export-ragas requires --mode answer")
        write_jsonl(args.export_ragas, ragas_records)
    if args.output:
        write_json(args.output, report)
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
