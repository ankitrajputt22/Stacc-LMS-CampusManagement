# Stacc AI service

The internal service behind Stacc's AI learning features: text extraction, chunking, a local vector index, and
course-grounded answers, summaries, practice questions, and assessment drafts from a local model. Only the Stacc
backend calls it, with the shared internal token; browsers never reach it. See `../docs/ai-learning.md`.

- `app/providers/` — `ModelProvider` and `EmbeddingProvider`, with the local Ollama provider and a deterministic fake.
- `app/extraction/` — bounded text extraction from PDF, TXT, DOCX, PPTX, and XLSX. No OCR.
- `app/indexing/` — chunking, index building, and the atomically replaced on-disk index.
- `app/retrieval/` — retrieval, guarded prompts, and checked structured output.
- `app/api/` — the `/health` and token-protected `/internal` routes.

Run the tests (no network, GPU, or model needed):

```bash
python3.12 -m pip install -r requirements-dev.txt
```
```bash
python -m pytest
```
```bash
ruff check .
```
