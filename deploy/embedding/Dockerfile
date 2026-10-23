FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    HF_HOME=/models/huggingface \
    TRANSFORMERS_CACHE=/models/huggingface

WORKDIR /app

# curl is used by the docker-compose healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Install CPU-only torch first so sentence-transformers does not pull the CUDA wheel.
RUN pip install torch --index-url https://download.pytorch.org/whl/cpu
COPY requirements.txt .
RUN pip install -r requirements.txt

COPY app.py .

EXPOSE 18091
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "18091"]
