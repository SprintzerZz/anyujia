#!/usr/bin/env python3
"""Offline, dependency-free evaluator for Legal Agent context compression."""

import argparse
import json
from pathlib import Path


def load_jsonl(path):
    rows = []
    for line_no, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            rows.append(json.loads(line))
        except json.JSONDecodeError as exc:
            raise ValueError(f"{path}:{line_no}: invalid JSON: {exc}") from exc
    return rows


def validate(cases):
    errors = []
    case_ids = set()
    for case in cases:
        case_id = case.get("case_id")
        if not case_id or case_id in case_ids:
            errors.append(f"duplicate or missing case_id: {case_id}")
        case_ids.add(case_id)
        for field in ("query", "chat_messages", "contract_blocks", "agent_history",
                      "expected_facts", "expected_evidence_ids"):
            if field not in case:
                errors.append(f"{case_id}: missing {field}")
        source_ids = {
            item.get("message_id") for item in case.get("chat_messages", [])
        } | {
            item.get("block_id") for item in case.get("contract_blocks", [])
        }
        source_ids.discard(None)
        expected = set(case.get("expected_evidence_ids", []))
        forbidden = set(case.get("forbidden_evidence_ids", []))
        if not expected <= source_ids:
            errors.append(f"{case_id}: expected evidence missing from source")
        if expected & forbidden:
            errors.append(f"{case_id}: evidence cannot be both expected and forbidden")
    return errors


def evaluate(cases, predictions):
    has_predictions = bool(predictions)
    prediction_map = {row.get("case_id"): row for row in predictions or []}
    rows = []
    for case in cases:
        prediction = prediction_map.get(case["case_id"], {})
        expected = set(case.get("expected_evidence_ids", []))
        selected = set(prediction.get("selected_evidence_ids", []))
        forbidden = set(case.get("forbidden_evidence_ids", []))
        facts_text = " ".join(prediction.get("facts", []))
        facts_hit = sum(1 for fact in case.get("expected_facts", []) if fact in facts_text)
        row = {
            "case_id": case["case_id"],
            "evidence_recall": len(expected & selected) / len(expected) if expected else 1.0,
            "forbidden_selected": sorted(forbidden & selected),
            "fact_recall": facts_hit / len(case.get("expected_facts", []))
            if case.get("expected_facts") else 1.0,
            "context_status_match": prediction.get("context_status")
            in (None, case.get("expected_context_status")),
        }
        rows.append(row)
    if not has_predictions:
        rows = [{"case_id": case["case_id"], "prediction_status": "MISSING"} for case in cases]
    denominator = len(rows) or 1
    report = {
        "dataset": "legal-context-v1",
        "cases": len(rows),
        "predictions_provided": has_predictions,
        "details": rows,
    }
    if has_predictions:
        report.update({
            "evidence_recall": sum(row["evidence_recall"] for row in rows) / denominator,
            "fact_recall": sum(row["fact_recall"] for row in rows) / denominator,
            "forbidden_violation_cases": sum(bool(row["forbidden_selected"]) for row in rows),
            "context_status_match_rate": sum(row["context_status_match"] for row in rows) / denominator,
        })
    else:
        report["evaluation_status"] = "DATASET_VALIDATED_ONLY"
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--predictions")
    parser.add_argument("--output")
    args = parser.parse_args()
    cases = load_jsonl(args.dataset)
    errors = validate(cases)
    if errors:
        raise SystemExit("Dataset validation failed:\n- " + "\n- ".join(errors))
    predictions = load_jsonl(args.predictions) if args.predictions else []
    report = evaluate(cases, predictions)
    output = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        Path(args.output).write_text(output + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
