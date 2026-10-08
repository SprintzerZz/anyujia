#!/usr/bin/env python3
"""Optional RAGAS judge for JSONL exported by evaluate.py."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path


def load_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    try:
        from openai import AsyncOpenAI
        from ragas import EvaluationDataset, evaluate
        from ragas.llms import llm_factory
        from ragas.metrics import FactualCorrectness, Faithfulness, LLMContextRecall
    except ImportError as exc:
        print(json.dumps({"ragas": "SKIPPED", "reason": f"optional dependency missing: {exc.name}"}))
        return
    if args.check:
        print(json.dumps({"ragas": "AVAILABLE"}))
        return
    if args.input is None:
        parser.error("--input is required unless --check is used")
    api_key = os.getenv("RAGAS_API_KEY", "")
    if not api_key:
        print(json.dumps({"ragas": "SKIPPED", "reason": "RAGAS_API_KEY is not configured"}))
        return
    client = AsyncOpenAI(api_key=api_key, base_url=os.getenv("RAGAS_BASE_URL") or None)
    llm = llm_factory(os.getenv("RAGAS_MODEL", "gpt-4o-mini"), client=client)
    dataset = EvaluationDataset.from_list(load_jsonl(args.input))
    result = evaluate(dataset=dataset,
                      metrics=[LLMContextRecall(), Faithfulness(), FactualCorrectness()], llm=llm)
    rendered = json.dumps(result.to_pandas().to_dict(orient="records"), ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
