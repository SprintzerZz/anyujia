"""Validate synthetic contract fixtures before sending them to Legal Agent.

This is an input/fixture integrity check, not a legal correctness evaluator.
It verifies that required risk clauses and declared noise are present in the
source files and produces a machine-readable report for CI or a later Tika
parse comparison.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).parent)
    parser.add_argument("--manifest", type=Path, default=Path("manifest.v1.jsonl"))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    root = args.root.resolve()
    manifest = args.manifest if args.manifest.is_absolute() else root / args.manifest
    results = []
    failed = False

    for line in manifest.read_text(encoding="utf-8").splitlines():
        case = json.loads(line)
        source = root / case["file"]
        text = source.read_text(encoding="utf-8")
        missing = [term for term in case["must_find"] if term not in text]
        missing_noise = [term for term in case.get("noise", []) if term not in text]
        item = {
            "id": case["id"],
            "file": case["file"],
            "risk_level": case["risk_level"],
            "source_chars": len(text),
            "must_find_count": len(case["must_find"]),
            "missing_terms": missing,
            "noise_count": len(case.get("noise", [])),
            "missing_noise": missing_noise,
            "valid": not missing and not missing_noise,
        }
        failed |= not item["valid"]
        results.append(item)

    report = {
        "dataset": "legal-contracts",
        "version": "v1",
        "case_count": len(results),
        "passed": sum(item["valid"] for item in results),
        "failed": sum(not item["valid"] for item in results),
        "results": results,
        "note": "Fixture integrity only; not a legal correctness assessment.",
    }
    output = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output, encoding="utf-8")
    print(output, end="")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
