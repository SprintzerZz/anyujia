#!/usr/bin/env python3
"""Evaluate legal conclusion entailment with an optional multilingual embedding model."""
import argparse
import json
import math
import re
from collections import Counter

LABELS = {"ENTAILMENT", "CONTRADICTION", "UNKNOWN"}
NEGATION = {"不", "不得", "不能", "无权", "禁止", "不应", "不可", "无需", "没有"}


def tokens(text):
    return [c for c in re.findall(r"[\u4e00-\u9fffA-Za-z0-9]", text or "")]


def lexical_score(premise, hypothesis):
    left = Counter(tokens(premise))
    right = Counter(tokens(hypothesis))
    if not right:
        return 0.0
    return sum(min(left[k], v) for k, v in right.items()) / sum(right.values())


def lexical_predict(premise, hypothesis):
    score = lexical_score(premise, hypothesis)
    premise_neg = bool(NEGATION.intersection(set(tokens(premise))))
    hypothesis_neg = bool(NEGATION.intersection(set(tokens(hypothesis))))
    if score >= 0.55 and premise_neg != hypothesis_neg:
        return "CONTRADICTION", score
    if score >= 0.60:
        return "ENTAILMENT", score
    return "UNKNOWN", score


def embedding_predict(rows, model_name):
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError:
        return None
    model = SentenceTransformer(model_name)
    vectors = model.encode([[row["premise"], row["hypothesis"]] for row in rows])
    predictions = []
    for row, pair in zip(rows, vectors):
        premise, hypothesis = pair
        dot = sum(a * b for a, b in zip(premise, hypothesis))
        norm = math.sqrt(sum(a * a for a in premise) * sum(b * b for b in hypothesis)) or 1.0
        score = dot / norm
        label, _ = lexical_predict(row["premise"], row["hypothesis"])
        if score >= 0.82 and label != "CONTRADICTION":
            label = "ENTAILMENT"
        elif score < 0.50:
            label = "UNKNOWN"
        predictions.append((label, score))
    return predictions


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--model")
    args = parser.parse_args()
    with open(args.dataset, encoding="utf-8") as handle:
        rows = [json.loads(line) for line in handle if line.strip()]
    if not rows or any(row.get("label") not in LABELS for row in rows):
        raise SystemExit("dataset must contain at least one row with a valid label")
    predictions = embedding_predict(rows, args.model) if args.model else None
    mode = "sentence-transformers" if predictions else "char-ngram-baseline"
    predictions = predictions or [lexical_predict(row["premise"], row["hypothesis"]) for row in rows]
    correct = sum(pred == row["label"] for row, (pred, _) in zip(rows, predictions))
    by_label = {}
    for label in LABELS:
        actual = [i for i, row in enumerate(rows) if row["label"] == label]
        by_label[label] = {
            "count": len(actual),
            "correct": sum(predictions[i][0] == label for i in actual),
        }
    print(json.dumps({"mode": mode, "count": len(rows), "accuracy": correct / len(rows),
                      "by_label": by_label}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
