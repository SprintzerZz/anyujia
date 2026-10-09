# Legal RAG retrieval and answer evaluation

The evaluator calls internal `/legal/knowledge/search` or `/legal/knowledge/answer` endpoints. It supports
fully deterministic CI metrics and optional export to RAGAS. It never requires judge-model credentials for
the Java build or self-tests.

The versioned seed set is `cases.v1.jsonl`; governance and review-state rules are in `DATASET.md`. Validate it:

```powershell
python deploy/eval/legal-rag/evaluate.py `
  --mode answer `
  --dataset deploy/eval/legal-rag/cases.v1.jsonl `
  --validate-dataset
```

Run offline metric self-tests:

```powershell
python deploy/eval/legal-rag/evaluate.py --self-test
python deploy/eval/legal-rag/ragas_evaluate.py --check
```

Run retrieval evaluation and persist a baseline:

```powershell
python deploy/eval/legal-rag/evaluate.py `
  --mode retrieval `
  --dataset deploy/eval/legal-rag/cases.v1.jsonl `
  --endpoint http://127.0.0.1:18086 `
  --output deploy/eval/legal-rag/retrieval-baseline.json
```

Run answer evaluation, persist a baseline, and export RAGAS input:

```powershell
python deploy/eval/legal-rag/evaluate.py `
  --mode answer `
  --dataset deploy/eval/legal-rag/cases.v1.jsonl `
  --endpoint http://127.0.0.1:18086 `
  --output deploy/eval/legal-rag/answer-baseline.json `
  --export-ragas deploy/eval/legal-rag/ragas-input.jsonl
```

Use `--baseline <report.json>` to include per-metric A/B differences. Retrieval metrics are Recall@K,
HitRate@K, MRR, and nDCG@K. Deterministic answer metrics are citation validity, expected-source citation
coverage, lexical groundedness proxy, reference keyword coverage, and scope compliance. Both modes include
mean, P50, P95, and P99 latency.

Current frozen local reports:

| Report | Cases | Key metrics | Latency |
| --- | ---: | --- | --- |
| `retrieval-final-warm.json` (`frozen-v1-expanded-final-warm`) | 34 retrieval cases | Recall@4 98.53%, HitRate@4 100.00%, MRR 0.8578, nDCG@4 0.8809 | P50 353.06 ms, P95 2401.13 ms |
| `answer-final.json` (`frozen-v1-expanded-final`) | 37 answer cases | Citation validity 100.00%, citation coverage 93.24%, keyword coverage 89.19%, scope compliance 94.59% | P50 345.15 ms, P95 706.69 ms |

These runs were made against `http://127.0.0.1:18086` after the Spring service had started. Treat retrieval
P95 as a diagnostic number because remote Redis/MySQL and vector/lexical fusion outliers dominate it; the
answer P95 is the better resume-facing latency number for the tenant-visible answer endpoint.

Milvus/BGE rerank benchmark (stack now wired):

| Run label | Stack | Report separately as | Target |
| --- | --- | --- | --- |
| `frozen-v1-milvus-bge-rerank` | Milvus Standalone + `BAAI/bge-large-zh-v1.5` + `BAAI/bge-reranker-v2-m3` | A new frozen report, not an overwrite of current numbers | Recall@5 >= 90%, MRR >= 0.75, citation validity >= 98%, scope compliance >= 98%, answer P95 <= 1200 ms |

To run this stack, start Milvus and the embedding service (`docker compose -f docker-compose-mid.yml up -d
frameworkjava-milvus frameworkjava-legal-embedding`), then start the service with
`LEGAL_MILVUS_ENABLED=true LEGAL_LOCAL_EMBEDDING_ENABLED=true LEGAL_LOCAL_RERANKER_ENABLED=true`, and backfill
the Milvus collection with `LegalMilvusBackfillCli`. Keep the current lexical/legal-intent pipeline enabled and
add Milvus vector recall plus neural reranking as additional stages. Report both the old and new run labels with
dataset SHA-256. If the benchmark uses new cases, increment the dataset version and do not claim quality
improvement against the older baseline.

Scope compliance should be measured with a larger negative set, not only in-domain rental questions. Add frozen
cases for non-rental civil disputes, criminal-law questions, procedural court guidance, personal finance advice,
and landlord-side action requests. A compliant answer should reject or narrow these questions before generation,
or answer only the rental-law portion with citations.

Optional judge-based RAGAS execution is deliberately separate:

```powershell
pip install ragas openai datasets
$env:RAGAS_API_KEY="<judge-key>"
$env:RAGAS_BASE_URL="<optional-openai-compatible-url>"
$env:RAGAS_MODEL="<judge-model>"
python deploy/eval/legal-rag/ragas_evaluate.py `
  --input deploy/eval/legal-rag/ragas-input.jsonl `
  --output deploy/eval/legal-rag/ragas-result.json
```

If RAGAS packages or credentials are absent, the optional runner reports `SKIPPED` instead of failing.
Do not commit judge credentials, tenant contracts, conversations, or personal data. Do not publish quality-uplift
percentages until the dataset is legally reviewed, version-frozen, and evaluated against a stored baseline.
