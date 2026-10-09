# Anyujia Legal Agent MVP

This module provides a tenant-private rental-contract risk assessment workflow. It is intentionally
separate from the two-party chat participant model: landlords cannot query tenant reports and the
agent cannot send messages on the tenant's behalf.

## API flow

1. Upload a PDF or DOCX with multipart `POST /legal/contracts/upload?conversationId=...`. The service
   validates the signature and limits, computes SHA-256, extracts text, and persists evidence blocks.
2. Alternatively, trusted existing extraction flows can call `POST /legal/contracts` with a private
   object key, SHA-256, and extracted text.
3. Administrators ingest official law text with `POST /legal/knowledge/sources`; the URL must be HTTPS.
4. Internal services can inspect ranked RAG passages with `POST /legal/knowledge/search` for diagnostics
   and labeled evaluation; this endpoint is not exposed to tenant clients.
5. Internal services can call `POST /legal/knowledge/answer` for a grounded answer, citation allow-list,
   generation mode, degradation flag, and retrieval/generation timing. It uses the same internal-only authorization.
6. `POST /legal/assessments` with `contractId`, a client-generated `requestId`, optional `regionCode`,
   and optional `asOfDate` (`yyyy-MM-dd`).
7. Poll `GET /legal/assessments/{jobId}` until `COMPLETED` or `FAILED`.

All endpoints require gateway-provided `userId`, `userType` and `userFrom` headers. Only app users
with tenant user type `1` are accepted. Contract registration verifies membership through the chat
service. Contracts, jobs and reports are always queried with the owning tenant ID.

## Environment

| Variable | Default | Purpose |
| --- | --- | --- |
| `LEGAL_AI_ENABLED` | `false` | Enable optional DashScope wording enhancement. |
| `DASHSCOPE_API_KEY` | empty | DashScope credential; never store this in source control. |
| `DASHSCOPE_BASE_URL` | compatible-mode endpoint | OpenAI-compatible DashScope API base URL. |
| `LEGAL_CHAT_MODEL` | `qwen-plus` | Chat model used to refine explanations. |
| `LEGAL_EMBEDDING_ENABLED` | `false` | Generate law-chunk embeddings through DashScope. |
| `LEGAL_EMBEDDING_MODEL` | `text-embedding-v4` | Embedding model name. |
| `LEGAL_EMBEDDING_DIMENSIONS` | `1024` | Validated embedding vector dimensions. |
| `LEGAL_VECTOR_ENABLED` | `false` | Mix cosine similarity with lexical retrieval. |
| `LEGAL_RETRIEVAL_CHANNEL_CANDIDATE_LIMIT` | `50` | Maximum candidates retained by each retrieval channel. |
| `LEGAL_RETRIEVAL_RERANK_CANDIDATE_LIMIT` | `30` | Fused candidates evaluated by deterministic reranking. |
| `LEGAL_RETRIEVAL_RRF_K` | `60` | Rank constant used by reciprocal-rank fusion. |
| `LEGAL_RETRIEVAL_LEXICAL_WEIGHT` | `1.0` | RRF weight for lexical retrieval. |
| `LEGAL_RETRIEVAL_VECTOR_WEIGHT` | `1.2` | RRF weight for embedding similarity. |
| `LEGAL_RETRIEVAL_INTENT_WEIGHT` | `0.8` | RRF weight for legal-intent/synonym retrieval. |
| `LEGAL_RETRIEVAL_RERANK_WEIGHT` | `0.35` | Weight of deterministic evidence-aware reranking. |
| `LEGAL_MILVUS_ENABLED` | `false` | Enable Milvus Standalone-backed vector retrieval; when disabled or unreachable, retrieval falls back to in-memory cosine. |
| `LEGAL_MILVUS_HOST` | `127.0.0.1` | Milvus host for local evaluation and development. |
| `LEGAL_MILVUS_PORT` | `19530` | Milvus gRPC port. |
| `LEGAL_MILVUS_DATABASE` | `default` | Milvus database name. |
| `LEGAL_MILVUS_COLLECTION` | `legal_source_chunk_v1` | Law/source chunk collection name. |
| `LEGAL_MILVUS_DIMENSION` | `1024` | Embedding dimension validated against the collection schema. |
| `LEGAL_MILVUS_TOP_K` | `50` | Vector candidates recalled per query from Milvus. |
| `LEGAL_MILVUS_CONNECT_TIMEOUT_MILLIS` | `10000` | Milvus client connect timeout. |
| `LEGAL_LOCAL_EMBEDDING_ENABLED` | `false` | Enable the local BGE embedding service (takes precedence over DashScope). |
| `LEGAL_LOCAL_EMBEDDING_ENDPOINT` | `http://127.0.0.1:18091/embed` | Local embedding endpoint. |
| `LEGAL_LOCAL_EMBEDDING_MODEL` | `BAAI/bge-large-zh-v1.5` | Local Chinese embedding model. |
| `LEGAL_LOCAL_EMBEDDING_DIMENSIONS` | `1024` | BGE large Chinese embedding dimension used for validation. |
| `LEGAL_LOCAL_RERANKER_ENABLED` | `false` | Enable the local BGE cross-encoder reranker. |
| `LEGAL_LOCAL_RERANKER_ENDPOINT` | `http://127.0.0.1:18091/rerank` | Local reranker endpoint. |
| `LEGAL_LOCAL_RERANKER_MODEL` | `BAAI/bge-reranker-v2-m3` | Local reranker model. |
| `LEGAL_RERANK_TOP_N` | `8` | Number of fused candidates sent to the neural reranker. |
| `LEGAL_MEMORY_REDIS_ENABLED` | `true` | Enable Redis short-term working memory for bounded chat windows. |
| `LEGAL_MEMORY_SHORT_TERM_TTL_SECONDS` | `1800` | TTL for Redis short-term legal memory entries. |
| `LEGAL_MEMORY_CHAT_WINDOW_LIMIT` | `100` | Maximum chat messages retained in the short-term legal memory window. |
| `LEGAL_DOCUMENT_MAX_BYTES` | `26214400` | Maximum uploaded contract bytes. |
| `LEGAL_DOCUMENT_MAX_PAGES` | `200` | Maximum parsed PDF pages. |
| `LEGAL_DOCUMENT_MAX_CHARACTERS` | `500000` | Maximum extracted characters. |
| `LEGAL_DOCUMENT_MAX_BLOCKS` | `5000` | Maximum persisted evidence blocks. |

PowerShell example (use a newly rotated key, not one pasted into chat):

```powershell
$env:DASHSCOPE_API_KEY="<set-locally>"
$env:LEGAL_AI_ENABLED="true"
$env:LEGAL_EMBEDDING_ENABLED="true"
$env:LEGAL_VECTOR_ENABLED="true"
$env:LEGAL_EMBEDDING_MODEL="text-embedding-v4"
$env:LEGAL_EMBEDDING_DIMENSIONS="1024"
```

AI enhancement is fail-safe: missing credentials, network errors, invalid JSON or model errors leave
the deterministic rule findings intact. Contract text is explicitly treated as untrusted data and
cannot grant tools or override system instructions. No live model call is made by unit tests.

The knowledge-answer endpoint follows the same policy. With AI enabled it asks DashScope for JSON containing
an answer and only numeric indexes from the supplied context. Unknown or out-of-range citations are discarded
and checked again while mapping the response. Missing credentials, timeouts, circuit-open state, invalid JSON,
or an unsupported citation set produce an extractive answer from the retrieved passages. Clearly unrelated
questions are rejected before retrieval. Every response identifies its mode and includes a legal-information disclaimer.

## Current scope

The rule layer detects contract risks, cross-checks up to 100 recent messages from the landlord for
deposit forfeiture, unannounced entry, and charges absent from the contract, and compares structured
house-publication promises against the contract. Product checks flag deposit months, non-monthly payment
cycles, property fees, and material rental requirements that appear in the publication snapshot but not
in the contract. Each finding can reference contract, chat, product, and law evidence. PDF evidence uses
exact one-based page numbers. DOCX cannot guarantee layout-independent pagination, so it exposes stable
paragraph indexes and `DOCX_PAGE_HINT` only when saved page-break markers exist.

Assessment runs also fetch the conversation-linked house publication snapshot through internal chat and
house APIs. The snapshot is appended to the model enhancement context with rent, deposit months, payment
cycle, property fee, rental requirements, description, and facilities, so risk wording can compare contract
terms, landlord chat statements, and published product promises. Missing chat or house evidence is treated
as a degraded context signal and does not fail the deterministic contract assessment.

## Runtime context and trace

Legal assessment uses layered context instead of storing legal evidence as opaque long-term Redis memory.
Redis is used only as short-term working memory for the bounded chat window, while MySQL remains the durable
audit store. A single `AssessmentRun` is keyed by `jobId`, `contractId`, `tenantId`, and `conversationId`,
then builds context from persisted contract text blocks, up to 100 recent chat messages, the
conversation-linked house publication snapshot, and the legal passages retrieved for each risk code. The
LLM enhancement prompt is sectioned as `[landlord_messages]`, `[rental_product]`, and `[retrieved_law]`;
contract text and supplemental context are independently capped before they are sent to the model.

The short-term Redis memory key is scoped by tenant, conversation, and requested window size. It caches only
the recent chat window with a TTL, is bounded by `LEGAL_MEMORY_CHAT_WINDOW_LIMIT`, and automatically falls
back to the chat Feign API if Redis is missing, misconfigured, or unavailable. This keeps repeated assessment
runs from repeatedly loading the same conversation window while avoiding Redis as the source of truth for
legal conclusions or evidence.

Each completed context assembly writes a `legal_assessment_context_snapshot` row. The row stores metadata and
SHA-256 hashes for the contract/block set, chat message window, house publication snapshot, retrieval result,
and aggregate context, but does not duplicate contract or chat content. This gives every report a reproducible
context fingerprint while keeping the source of truth in the contract, chat, house, and knowledge tables.

Each assessment writes append-only `legal_trace_event` rows for `ASSESSMENT_RUN`, `CONTRACT_CONTEXT`,
`CONTRACT_RULE_ANALYSIS`, `CHAT_EVIDENCE_ANALYSIS`, `PRODUCT_CONTEXT`, `PRODUCT_RISK_ANALYSIS`,
`LEGAL_RETRIEVAL`, `ASSESSMENT_CONTEXT_SNAPSHOT`, `AI_RISK_ENHANCEMENT`, and `PERSIST_RISK_REPORT`.
Trace details record stage status, duration, input sizes, risk counts, retrieved passage counts,
`contextSnapshotId`, `contextHash`, and bounded failure metadata without blocking the user-facing report.
Risk evidence is persisted separately as `CONTRACT`, `CHAT`, `PRODUCT`, or `LAW` evidence refs, so the
tenant-facing result can point back to the exact source category used by the assessment.

RAG first filters active sources by region and assessment date. Lexical, vector, and legal-intent
channels are ranked independently and combined with configurable weighted reciprocal-rank fusion.
A deterministic reranker then considers query-term coverage, exact phrases, article-level evidence,
and official-source signals. Vector failures leave the lexical and legal-intent channels operational.
Candidate budgets and final context budgets are configured independently.

## Milvus/BGE vector retrieval

Vector retrieval is backed by Milvus Standalone with local `BAAI/bge-large-zh-v1.5` embeddings and a
`BAAI/bge-reranker-v2-m3` cross-encoder reranker, wired through the same retrieval pipeline described above.
Enable them with `LEGAL_MILVUS_ENABLED`, `LEGAL_LOCAL_EMBEDDING_ENABLED`, and `LEGAL_LOCAL_RERANKER_ENABLED`.
When local embedding is enabled it takes precedence over the optional DashScope embedding; the vector store
and reranker each fall back gracefully when their backing service is unavailable.

The `legal_source_chunk_v1` collection stores each law or public-source chunk with a stable chunk ID, source
ID, title/article metadata, region, source URL, ACTIVE/STALE state, and a 1024-dimensional embedding (HNSW
index, cosine metric). Retrieval keeps the existing legal-intent and BM25-style lexical channels, adds Milvus
vector top-N recall, fuses the channels with reciprocal-rank fusion, then sends a bounded fused candidate set
to the BGE reranker before the final answer context is assembled.

The degradation path is explicit: if Milvus, the embedding service, or the reranker is unavailable, the
assessment still runs with contract rules, chat/product consistency checks, lexical/legal-intent law
retrieval, and extractive citation fallback. A failed retrieval enhancement never blocks tenant-visible
risk reports.

The local embedding/reranker service lives under `deploy/embedding/` (FastAPI + sentence-transformers) and
is orchestrated alongside Milvus (etcd + MinIO + standalone) in `deploy/dev/app/docker-compose-mid.yml`.
Ingestion and backfill write chunks into both MySQL and Milvus; `LegalMilvusBackfillCli` backfills an
existing MySQL corpus into a fresh Milvus collection.

Scope-compliance improvement work should focus on answer gating rather than only model quality: strengthen
domain classification before retrieval, maintain an allow-list of rental-law intents, reject out-of-scope
questions before generation, require every generated claim to map to cited evidence, and add negative frozen
cases for non-rental, procedural, and personal-advice questions. The target for the next frozen run is to move
scope compliance from 94.59% to at least 98% without reducing citation validity below 98%.

The repository-local evaluator under `deploy/eval/legal-rag` supports retrieval and answer modes. Retrieval
mode reports Recall@K, HitRate@K, MRR, and nDCG@K. Answer mode reports citation validity and coverage,
a deterministic groundedness proxy, required-keyword coverage, scope compliance, and latency percentiles.
Reports include run metadata such as run label, dataset SHA-256, evaluator SHA-256, dataset version counts,
review-state counts, category distribution, endpoint, dirty-worktree flag, and weak-metric examples. It can
export the canonical RAGAS dataset shape; the optional RAGAS judge is isolated from the Java build.
Quality-improvement percentages must only be reported from a frozen, reviewed dataset and stored baseline.

Latest local frozen-set numbers are stored under `deploy/eval/legal-rag`: retrieval
`frozen-v1-expanded-final-warm` covers 34 source-verified retrieval cases with Recall@4 98.53%,
HitRate@4 100.00%, MRR 0.8578, nDCG@4 0.8809, and P95 retrieval latency 2401.13 ms. Answer
`frozen-v1-expanded-final` covers 37 source-verified answer cases with citation validity 100.00%,
citation coverage 93.24%, keyword coverage 89.19%, scope compliance 94.59%, and P95 answer latency
706.69 ms. These are local post-start service results from a dirty worktree, so publish them with the
run label and dataset hash rather than as production SLOs.

Resume-safe phrasing for the current implemented state:

- Built a tenant-private rental legal agent that assembles contract clauses, landlord-tenant chat evidence,
  house-publication product facts, and reviewed law passages into an append-only assessment trace; each report
  links findings back to CONTRACT/CHAT/PRODUCT/LAW evidence categories and a reproducible context snapshot.
- Implemented hybrid legal retrieval with lexical, legal-intent, and vector recall, weighted fusion, and
  deterministic evidence-aware reranking; on the frozen local set it reached Recall@4 98.53%, HitRate@4
  100.00%, MRR 0.8578, citation validity 100.00%, citation coverage 93.24%, and answer P95 706.69 ms.
- Migrated vector recall to Milvus Standalone with local `BAAI/bge-large-zh-v1.5` embeddings and a
  `BAAI/bge-reranker-v2-m3` cross-encoder reranker, keeping in-memory cosine, lexical/legal-intent, and
  extractive citation as fallbacks when the vector stack degrades.
- Added Redis-bounded short-term chat memory and MySQL context snapshots for legal assessment runs, capturing
  context hashes, retrieval metadata, trace stages, and degraded-context signals without storing legal
  conclusions as opaque long-term Redis memory.

Image-only/scanned PDFs currently return an OCR-required parsing error. Uploaded originals are archived
through the private file-service/OSS flow before parsed blocks and evidence references are persisted.
Results are AI-assisted risk information and do not constitute legal advice.
