"""Deterministic chunking: bounded, overlapping pieces that never cross a page or slide.

Whitespace is normalised, then each segment is cut into pieces of about ``size`` characters. A cut is
moved back to the nearest paragraph, sentence, or word break when one is close, and consecutive pieces
share about ``overlap`` characters so an idea split by a cut is still found.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from app.extraction.extractors import Segment

SPACES = re.compile(r"[ \t\f\v\r]+")
BLANK_LINES = re.compile(r"\n\s*\n+")


@dataclass(frozen=True)
class TextChunk:
    text: str
    page_number: int | None
    slide_number: int | None


def normalise(text: str) -> str:
    text = SPACES.sub(" ", text)
    text = BLANK_LINES.sub("\n\n", text)
    return "\n".join(line.strip() for line in text.split("\n")).strip()


def _cut_point(text: str, start: int, size: int) -> int:
    end = start + size
    if end >= len(text):
        return len(text)
    window_start = start + size // 2
    for pattern in ("\n\n", ". ", "? ", "! ", "\n", " "):
        found = text.rfind(pattern, window_start, end)
        if found != -1:
            return found + len(pattern)
    return end


def chunk_segments(segments: list[Segment], size: int, overlap: int) -> list[TextChunk]:
    chunks: list[TextChunk] = []
    for segment in segments:
        text = normalise(segment.text)
        start = 0
        while start < len(text):
            end = _cut_point(text, start, size)
            piece = text[start:end].strip()
            if piece:
                chunks.append(TextChunk(piece, segment.page_number, segment.slide_number))
            if end >= len(text):
                break
            # Step back for the overlap, but always move forward, and start at a word boundary.
            next_start = max(end - overlap, start + 1)
            space = text.find(" ", next_start, end)
            start = space + 1 if space != -1 else next_start
    return chunks
