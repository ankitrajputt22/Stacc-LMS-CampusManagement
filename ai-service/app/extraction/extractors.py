from __future__ import annotations

import io
import logging
import re
import zipfile
from dataclasses import dataclass

from defusedxml import ElementTree

from app.models.schemas import FileType

log = logging.getLogger(__name__)

MAX_PDF_PAGES = 500
MAX_ZIP_MEMBERS = 5000
MAX_XML_MEMBER_BYTES = 25 * 1024 * 1024
MAX_ZIP_TOTAL_BYTES = 200 * 1024 * 1024
MAX_SPREADSHEET_CELLS = 100_000

W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
A = "{http://schemas.openxmlformats.org/drawingml/2006/main}"
P = "{http://schemas.openxmlformats.org/presentationml/2006/main}"
S = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
R = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"
REL = "{http://schemas.openxmlformats.org/package/2006/relationships}"


class ExtractionError(Exception):
    pass


@dataclass(frozen=True)
class Segment:

    text: str
    page_number: int | None = None
    slide_number: int | None = None
    heading: str | None = None


class _Budget:

    def __init__(self, max_chars: int) -> None:
        self.remaining = max_chars

    def take(self, text: str) -> str:
        text = text[: max(self.remaining, 0)]
        self.remaining -= len(text)
        return text

    @property
    def spent(self) -> bool:
        return self.remaining <= 0


def extract(file_type: FileType, data: bytes, max_chars: int) -> list[Segment]:
    budget = _Budget(max_chars)
    try:
        if file_type is FileType.TXT:
            segments = _text(data, budget)
        elif file_type is FileType.PDF:
            segments = _pdf(data, budget)
        elif file_type is FileType.DOCX:
            segments = _docx(data, budget)
        elif file_type is FileType.PPTX:
            segments = _pptx(data, budget)
        elif file_type is FileType.XLSX:
            segments = _xlsx(data, budget)
        else:  # pragma: no cover - the enum is closed
            raise ExtractionError("unsupported file type")
    except ExtractionError:
        raise
    except Exception as error:
        raise ExtractionError(type(error).__name__) from error
    return [segment for segment in segments if segment.text.strip()]


def _text(data: bytes, budget: _Budget) -> list[Segment]:
    if b"\x00" in data[:8192]:
        raise ExtractionError("not plain text")
    return [Segment(budget.take(data.decode("utf-8-sig", errors="replace")))]


def _pdf(data: bytes, budget: _Budget) -> list[Segment]:
    from pypdf import PdfReader

    reader = PdfReader(io.BytesIO(data), strict=False)
    if reader.is_encrypted:
        try:
            if not reader.decrypt(""):
                raise ExtractionError("encrypted")
        except Exception as error:
            raise ExtractionError("encrypted") from error
    pages = []
    for number, page in enumerate(reader.pages, start=1):
        if number > MAX_PDF_PAGES or budget.spent:
            break
        pages.append(page.extract_text() or "")
    return [Segment(budget.take(text), page_number=number) for number, text in enumerate(without_running_lines(pages), start=1)]


EDGE_LINES = 2
DIGITS = re.compile(r"\d+")


def _edge_key(line: str) -> str:
    return DIGITS.sub("#", " ".join(line.split()).lower())


def without_running_lines(pages: list[str]) -> list[str]:
    if len(pages) < 3:
        return pages
    counts: dict[str, int] = {}
    for text in pages:
        lines = [line for line in text.split("\n") if line.strip()]
        for key in {_edge_key(line) for line in lines[:EDGE_LINES] + lines[-EDGE_LINES:]}:
            counts[key] = counts.get(key, 0) + 1
    running = {key for key, count in counts.items() if count >= max(3, (len(pages) + 1) // 2)}
    if not running:
        return pages
    cleaned = []
    for text in pages:
        lines = [line for line in text.split("\n") if line.strip()]
        edge = set(range(min(EDGE_LINES, len(lines)))) | set(range(max(len(lines) - EDGE_LINES, 0), len(lines)))
        kept = [line for index, line in enumerate(lines) if not (index in edge and _edge_key(line) in running)]
        cleaned.append("\n".join(kept) if kept else text)
    return cleaned


def _open_package(data: bytes) -> zipfile.ZipFile:
    package = zipfile.ZipFile(io.BytesIO(data))
    members = package.infolist()
    if len(members) > MAX_ZIP_MEMBERS or sum(member.file_size for member in members) > MAX_ZIP_TOTAL_BYTES:
        raise ExtractionError("package too large")
    return package


def _xml(package: zipfile.ZipFile, name: str):
    with package.open(name) as member:
        content = member.read(MAX_XML_MEMBER_BYTES + 1)
    if len(content) > MAX_XML_MEMBER_BYTES:
        raise ExtractionError("member too large")
    return ElementTree.fromstring(content, forbid_dtd=True)


def _docx(data: bytes, budget: _Budget) -> list[Segment]:
    with _open_package(data) as package:
        body = _xml(package, "word/document.xml")
    segments: list[Segment] = []
    heading: str | None = None
    paragraphs: list[str] = []

    def close() -> None:
        if paragraphs:
            segments.append(Segment(budget.take("\n".join(paragraphs)), heading=heading))

    for paragraph in body.iter(f"{W}p"):
        style = next((node.get(f"{W}val", "") for node in paragraph.iter(f"{W}pStyle")), "")
        parts = []
        for node in paragraph.iter():
            if node.tag == f"{W}t" and node.text:
                parts.append(node.text)
            elif node.tag in (f"{W}tab",):
                parts.append("\t")
            elif node.tag in (f"{W}br", f"{W}cr"):
                parts.append("\n")
        text = "".join(parts)
        if style.lower().startswith(("heading", "title")) and text.strip():
            close()
            paragraphs = []
            heading = text.strip()[:200]
        else:
            paragraphs.append(text)
        if budget.spent:
            break
    close()
    return segments


def _slide_order(package: zipfile.ZipFile) -> list[str]:
    names = {name for name in package.namelist() if re.fullmatch(r"ppt/slides/slide\d+\.xml", name)}
    try:
        presentation = _xml(package, "ppt/presentation.xml")
        relations = _xml(package, "ppt/_rels/presentation.xml.rels")
        targets = {rel.get("Id"): rel.get("Target", "") for rel in relations.iter(f"{REL}Relationship")}
        ordered = []
        for slide in presentation.iter(f"{P}sldId"):
            target = targets.get(slide.get(f"{R}id"), "")
            name = "ppt/" + target.lstrip("/").removeprefix("ppt/")
            if name in names and name not in ordered:
                ordered.append(name)
        if ordered:
            return ordered
    except (KeyError, ExtractionError, ElementTree.ParseError):
        pass
    return sorted(names, key=lambda name: int(re.search(r"(\d+)\.xml$", name).group(1)))


def _pptx(data: bytes, budget: _Budget) -> list[Segment]:
    segments = []
    with _open_package(data) as package:
        for number, name in enumerate(_slide_order(package), start=1):
            if budget.spent:
                break
            slide = _xml(package, name)
            lines = ["".join(node.text or "" for node in paragraph.iter(f"{A}t"))
                     for paragraph in slide.iter(f"{A}p")]
            kept = [line for line in lines if line.strip()]
            segments.append(Segment(budget.take("\n".join(kept)), slide_number=number,
                                    heading=kept[0].strip()[:200] if kept else None))
    return segments


def _xlsx(data: bytes, budget: _Budget) -> list[Segment]:
    segments = []
    cells = 0
    with _open_package(data) as package:
        shared: list[str] = []
        if "xl/sharedStrings.xml" in package.namelist():
            for item in _xml(package, "xl/sharedStrings.xml").iter(f"{S}si"):
                shared.append("".join(node.text or "" for node in item.iter(f"{S}t")))
        sheets = sorted(
            (name for name in package.namelist() if re.fullmatch(r"xl/worksheets/sheet\d+\.xml", name)),
            key=lambda name: int(re.search(r"(\d+)\.xml$", name).group(1)),
        )
        for name in sheets:
            if budget.spent:
                break
            rows = []
            for row in _xml(package, name).iter(f"{S}row"):
                values = []
                for cell in row.iter(f"{S}c"):
                    cells += 1
                    if cells > MAX_SPREADSHEET_CELLS:
                        break
                    values.append(_cell_text(cell, shared))
                rows.append("\t".join(value for value in values if value))
                if cells > MAX_SPREADSHEET_CELLS:
                    break
            segments.append(Segment(budget.take("\n".join(row for row in rows if row))))
    return segments


def _cell_text(cell, shared: list[str]) -> str:
    kind = cell.get("t")
    if kind == "inlineStr":
        return "".join(node.text or "" for node in cell.iter(f"{S}t"))
    value = cell.find(f"{S}v")
    if value is None or value.text is None:
        return ""
    if kind == "s":
        index = int(value.text)
        return shared[index] if 0 <= index < len(shared) else ""
    return value.text
