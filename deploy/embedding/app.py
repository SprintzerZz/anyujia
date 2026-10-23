"""Local embedding, reranker and entailment service for the legal RAG pipeline.

Serves models over HTTP:
  * BAAI/bge-large-zh-v1.5  -> /embed   (1024-dim dense embeddings)
  * BAAI/bge-reranker-v2-m3 -> /rerank  (cross-encoder relevance scores)
  * configurable NLI CrossEncoder -> /entailment (optional)

The /embed response mirrors the OpenAI/DashScope shape so the Java
``BgeLegalEmbeddingClient`` can parse it with the same code path as DashScope.
"""
import math
import os
import queue
import threading
import time
from concurrent.futures import Future, TimeoutError as FutureTimeoutError

import torch
import torch.nn.functional as F
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sentence_transformers import CrossEncoder, SentenceTransformer
from transformers import AutoModelForSequenceClassification, AutoTokenizer

EMBEDDING_MODEL = os.getenv("LEGAL_LOCAL_EMBEDDING_MODEL", "BAAI/bge-large-zh-v1.5")
RERANKER_MODEL = os.getenv("LEGAL_LOCAL_RERANKER_MODEL", "BAAI/bge-reranker-v2-m3")
ENTAILMENT_MODEL = os.getenv(
    "LEGAL_LOCAL_ENTAILMENT_MODEL", "IDEA-CCNL/Erlangshen-Roberta-110M-NLI"
).strip()
DEVICE = os.getenv("EMBEDDING_DEVICE", "cuda" if torch.cuda.is_available() else "cpu")

app = FastAPI(title="anyujia-legal-embedding")

MAX_TEXT_LENGTH = int(os.getenv("LEGAL_MAX_TEXT_LENGTH", "12000"))
MAX_EMBED_BATCH = int(os.getenv("LEGAL_MAX_EMBED_BATCH", "64"))
MAX_RERANK_DOCUMENTS = int(os.getenv("LEGAL_MAX_RERANK_DOCUMENTS", "64"))
MAX_ENTAILMENT_QUEUE = int(os.getenv("LEGAL_ENTAILMENT_QUEUE_SIZE", "64"))
ENTAILMENT_BATCH_SIZE = int(os.getenv("LEGAL_ENTAILMENT_BATCH_SIZE", "8"))
ENTAILMENT_BATCH_WAIT_MS = int(os.getenv("LEGAL_ENTAILMENT_BATCH_WAIT_MS", "20"))
ENTAILMENT_QUEUE_TIMEOUT_MS = int(os.getenv("LEGAL_ENTAILMENT_QUEUE_TIMEOUT_MS", "2500"))
ENTAILMENT_INT8 = os.getenv("LEGAL_ENTAILMENT_INT8", "true").lower() == "true"
ENABLE_EMBEDDING = os.getenv("LEGAL_ENABLE_EMBEDDING", "true").lower() == "true"
ENABLE_RERANKER = os.getenv("LEGAL_ENABLE_RERANKER", "true").lower() == "true"

embedding_model = SentenceTransformer(EMBEDDING_MODEL, device=DEVICE) if ENABLE_EMBEDDING else None
reranker_model = CrossEncoder(RERANKER_MODEL, device=DEVICE, max_length=512) if ENABLE_RERANKER else None
entailment_model = None
entailment_tokenizer = None
entailment_loading = bool(ENTAILMENT_MODEL)
entailment_load_error = None
entailment_load_attempt = 0
entailment_queue = queue.Queue(maxsize=MAX_ENTAILMENT_QUEUE)


def _load_entailment_model():
    global entailment_model, entailment_tokenizer, entailment_loading
    global entailment_load_error, entailment_load_attempt
    if not ENTAILMENT_MODEL:
        entailment_loading = False
        return
    max_attempts = int(os.getenv("LEGAL_ENTAILMENT_LOAD_RETRIES", "3"))
    for attempt in range(1, max_attempts + 1):
        entailment_load_attempt = attempt
        try:
            tokenizer = AutoTokenizer.from_pretrained(ENTAILMENT_MODEL)
            model = AutoModelForSequenceClassification.from_pretrained(ENTAILMENT_MODEL).to(DEVICE).eval()
            if ENTAILMENT_INT8 and DEVICE == "cpu":
                model = torch.quantization.quantize_dynamic(
                    model, {torch.nn.Linear}, dtype=torch.qint8
                ).eval()
            entailment_tokenizer = tokenizer
            entailment_model = model
            entailment_load_error = None
            entailment_loading = False
            return
        except Exception as exc:
            entailment_load_error = f"{type(exc).__name__}: {str(exc)[:240]}"
            if attempt < max_attempts:
                time.sleep(min(30, 5 * attempt))
    entailment_loading = False


if ENTAILMENT_MODEL:
    threading.Thread(target=_load_entailment_model, name="entailment-model-loader", daemon=True).start()


def _run_nli_batch(batch):
    premises = [item[0] for item in batch]
    hypotheses = [item[1] for item in batch]
    tokens = entailment_tokenizer(
        premises,
        hypotheses,
        return_tensors="pt",
        truncation=True,
        padding=True,
        max_length=512,
    )
    tokens = {key: value.to(DEVICE) for key, value in tokens.items()}
    with torch.inference_mode():
        probabilities = F.softmax(entailment_model(**tokens).logits, dim=-1)
    results = []
    for index, values in enumerate(probabilities):
        label_index = int(torch.argmax(values).item())
        results.append({
            "label": str(entailment_model.config.id2label.get(label_index, "UNKNOWN")).upper(),
            "score": float(values[label_index].item()),
            "model": ENTAILMENT_MODEL,
            "model_type": "nli",
        })
    return results


def _entailment_batch_worker():
    while True:
        first = entailment_queue.get()
        batch = [first]
        deadline = time.monotonic() + ENTAILMENT_BATCH_WAIT_MS / 1000
        while len(batch) < ENTAILMENT_BATCH_SIZE:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                break
            try:
                batch.append(entailment_queue.get(timeout=remaining))
            except queue.Empty:
                break
        try:
            if entailment_model is None or entailment_tokenizer is None:
                raise RuntimeError("entailment model is not ready")
            results = _run_nli_batch(batch)
            for (_, _, future), result in zip(batch, results):
                future.set_result(result)
        except Exception as exc:
            for (_, _, future) in batch:
                future.set_exception(exc)
        finally:
            for _ in batch:
                entailment_queue.task_done()


threading.Thread(target=_entailment_batch_worker, name="entailment-batch-worker", daemon=True).start()


class EmbedRequest(BaseModel):
    model: str | None = None
    input: list[str]


class RerankRequest(BaseModel):
    query: str
    documents: list[str]


class EntailmentRequest(BaseModel):
    premise: str
    hypothesis: str
    model: str | None = None


@app.get("/health")
def health():
    return {
        "status": "ok" if (not entailment_loading or entailment_model is not None) else "degraded",
        "embedding_model": EMBEDDING_MODEL,
        "reranker_model": RERANKER_MODEL,
        "embedding_enabled": ENABLE_EMBEDDING,
        "reranker_enabled": ENABLE_RERANKER,
        "entailment_model": ENTAILMENT_MODEL or None,
        "entailment_mode": "nli" if entailment_model else "semantic-relevance-proxy",
        "entailment_ready": entailment_model is not None,
        "entailment_loading": entailment_loading,
        "entailment_load_attempt": entailment_load_attempt,
        "entailment_load_error": entailment_load_error,
        "entailment_queue_size": MAX_ENTAILMENT_QUEUE,
        "entailment_batch_size": ENTAILMENT_BATCH_SIZE,
        "entailment_batch_wait_ms": ENTAILMENT_BATCH_WAIT_MS,
        "entailment_int8": ENTAILMENT_INT8 and DEVICE == "cpu",
        "entailment_queue_timeout_ms": ENTAILMENT_QUEUE_TIMEOUT_MS,
    }


@app.get("/ready")
def ready():
    if entailment_model is None:
        raise HTTPException(503, "entailment model is not ready")
    return {"status": "ready", "model": ENTAILMENT_MODEL, "model_type": "nli"}


@app.post("/embed")
def embed(req: EmbedRequest):
    if embedding_model is None:
        raise HTTPException(503, "embedding model is disabled")
    if not req.input or len(req.input) > MAX_EMBED_BATCH:
        raise HTTPException(400, f"input batch must contain 1-{MAX_EMBED_BATCH} items")
    if any(not text.strip() or len(text) > MAX_TEXT_LENGTH for text in req.input):
        raise HTTPException(400, f"each input must be non-empty and <= {MAX_TEXT_LENGTH} chars")
    vectors = embedding_model.encode(
        req.input, normalize_embeddings=True, batch_size=16, show_progress_bar=False
    ).tolist()
    return {"data": [{"index": i, "embedding": v} for i, v in enumerate(vectors)]}


@app.post("/rerank")
def rerank(req: RerankRequest):
    if reranker_model is None:
        raise HTTPException(503, "reranker model is disabled")
    if not req.query.strip() or len(req.query) > MAX_TEXT_LENGTH:
        raise HTTPException(400, f"query must be non-empty and <= {MAX_TEXT_LENGTH} chars")
    if not req.documents or len(req.documents) > MAX_RERANK_DOCUMENTS:
        raise HTTPException(400, f"documents must contain 1-{MAX_RERANK_DOCUMENTS} items")
    if any(not doc.strip() or len(doc) > MAX_TEXT_LENGTH for doc in req.documents):
        raise HTTPException(400, f"each document must be non-empty and <= {MAX_TEXT_LENGTH} chars")
    pairs = [[req.query, doc] for doc in req.documents]
    scores = reranker_model.predict(pairs, show_progress_bar=False).tolist()
    return {"results": [{"index": i, "score": float(s)} for i, s in enumerate(scores)]}


def _probability(score: float) -> float:
    """Convert a scalar CrossEncoder output into a stable [0, 1] score."""
    score = float(score)
    if 0.0 <= score <= 1.0:
        return score
    return 1.0 / (1.0 + math.exp(-max(-40.0, min(40.0, score))))


@app.post("/entailment")
def entailment(req: EntailmentRequest):
    """Return an entailment-compatible response for the Java legal scorer.

    A configured NLI model is authoritative. Without one, the existing Chinese
    reranker is deliberately exposed as a semantic-relevance proxy, so callers
    can keep the pipeline available while distinguishing it from legal NLI.
    """
    if (not req.premise.strip() or not req.hypothesis.strip()
            or len(req.premise) > MAX_TEXT_LENGTH
            or len(req.hypothesis) > MAX_TEXT_LENGTH):
        raise HTTPException(400, f"premise and hypothesis must be non-empty and <= {MAX_TEXT_LENGTH} chars")
    if entailment_model and entailment_tokenizer:
        future = Future()
        try:
            entailment_queue.put_nowait((req.premise, req.hypothesis, future))
        except queue.Full:
            raise HTTPException(
                429,
                "entailment capacity is busy; retry with backoff",
                headers={"Retry-After": "1"},
            )
        try:
            return future.result(timeout=ENTAILMENT_QUEUE_TIMEOUT_MS / 1000)
        except FutureTimeoutError:
            raise HTTPException(504, "entailment queue timeout")
        except HTTPException:
            raise
        except Exception as exc:
            raise HTTPException(503, f"entailment inference failed: {str(exc)[:120]}")
    elif entailment_loading:
        raise HTTPException(503, "entailment model is loading")
    else:
        if reranker_model is None:
            raise HTTPException(503, "entailment model is unavailable")
        raw_score = reranker_model.predict(
            [[req.premise, req.hypothesis]], show_progress_bar=False
        )[0]
        score = _probability(raw_score)
        mode = "semantic-relevance-proxy"
        label = "ENTAILMENT" if score >= 0.85 else "UNKNOWN"
    return {
        "label": label,
        "score": score,
        "model": ENTAILMENT_MODEL or RERANKER_MODEL,
        "model_type": mode,
    }
