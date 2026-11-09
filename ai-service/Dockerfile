# The internal Stacc AI service. No model is downloaded here: models live in the separate Ollama
# service and are pulled once by the operator (see docs/ai-learning.md).
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /service
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# Runs as an unprivileged user; only the index directory is writable.
RUN useradd --system --uid 10001 --home-dir /service stacc-ai \
    && mkdir -p /data/indexes && chown stacc-ai /data/indexes
USER stacc-ai

ENV AI_INDEX_DIR=/data/indexes
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=4)"]
CMD ["uvicorn", "app.main:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
