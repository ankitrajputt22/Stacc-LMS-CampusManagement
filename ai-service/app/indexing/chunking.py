"""Deterministic chunking: bounded, overlapping pieces measured in tokens that never cross a page, slide, or
heading (v0.13.3).

Whitespace is normalised, then each segment is cut into pieces of about ``size`` tokens, a token being a word or a
punctuation mark. A cut is moved back to the nearest paragraph, sentence, or line break in the second half of the
piece, and consecutive pieces share about ``overlap`` tokens so an idea split by a cut is still found. Markdown
headings in topic text and plain-text files start new segments, and each chunk keeps its segment's heading.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from app.extraction.extractors import Segment

SPACES = re.compile(r"[ \t\f\v\r]+")
BLANK_LINES = re.compile(r"\n\s*\n+")
TOKEN = re.compile(r"\w+|[^\w\s]")
MARKDOWN_HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*#*\s*$")


@dataclass(frozen=True)
class TextChunk:
    text: str
    page_number: int | None
    slide_number: int | None
    heading: str | None = None


def normalise(text: str) -> str:
    text = SPACES.sub(" ", text)
    text = BLANK_LINES.sub("\n\n", text)
    return "\n".join(line.strip() for line in text.split("\n")).strip()


def count_tokens(text: str) -> int:
    return len(TOKEN.findall(text))


def split_headings(text: str) -> list[Segment]:
    """Text cut at its Markdown headings; each section keeps its heading path, such as ``Joins > Inner joins``."""
    segments: list[Segment] = []
    path: list[tuple[int, str]] = []
    lines: list[str] = []

    def close() -> None:
        body = "\n".join(lines).strip()
        if body:
            segments.append(Segment(body, heading=" > ".join(title for _, title in path)[:200] or None))

    for line in text.split("\n"):
        match = MARKDOWN_HEADING.match(line.strip())
        if match:
            close()
            lines = []
            level = len(match.group(1))
            path = [item for item in path if item[0] < level] + [(level, match.group(2).strip())]
        else:
            lines.append(line)
    close()
    return segments


def _cut(text: str, spans: list[tuple[int, int]], start: int, end: int) -> int:
    """The token index to end a piece at: a paragraph, sentence, or line break in the second half, else ``end``."""
    low = start + (end - start) // 2
    gaps = [(i, text[spans[i][1]:spans[i + 1][0]]) for i in range(end - 1, low - 1, -1) if i + 1 < len(spans)]
    for found in (
        lambda i, gap: "\n\n" in gap,
        lambda i, gap: text[spans[i][0]:spans[i][1]] in ".?!" and gap != "",
        lambda i, gap: "\n" in gap,
    ):
        for i, gap in gaps:
            if found(i, gap):
                return i + 1
    return end


def chunk_segments(segments: list[Segment], size: int, overlap: int) -> list[TextChunk]:
    chunks: list[TextChunk] = []
    for segment in segments:
        text = normalise(segment.text)
        spans = [match.span() for match in TOKEN.finditer(text)]
        start = 0
        while start < len(spans):
            end = min(start + size, len(spans))
            if end < len(spans):
                end = _cut(text, spans, start, end)
            piece = text[spans[start][0]:spans[end - 1][1]].strip()
            if piece:
                chunks.append(TextChunk(piece, segment.page_number, segment.slide_number, segment.heading))
            if end >= len(spans):
                break
            # Step back for the overlap, but always move forward.
            start = max(end - overlap, start + 1)
    return chunks
