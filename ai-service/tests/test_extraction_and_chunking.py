from __future__ import annotations

import io
import zipfile

import pytest

from app.extraction.extractors import ExtractionError, Segment, extract
from app.indexing.chunking import chunk_segments, normalise
from app.models.schemas import FileType
from helpers import docx_bytes, pdf_bytes, pptx_bytes, xlsx_bytes


def test_pdf_keeps_page_numbers():
    segments = extract(FileType.PDF, pdf_bytes(["First page about keys", "Second page about normal forms"]), 10_000)
    assert [(s.page_number, "normal forms" in s.text) for s in segments] == [(1, False), (2, True)]


def test_pptx_keeps_presentation_slide_order():
    segments = extract(FileType.PPTX, pptx_bytes(["Intro slide", "Joins slide", "Indexes slide"]), 10_000)
    assert [(s.slide_number, s.text) for s in segments] == [(1, "Intro slide"), (2, "Joins slide"), (3, "Indexes slide")]


def test_docx_text_has_no_page_numbers():
    segments = extract(FileType.DOCX, docx_bytes(["Normalisation removes redundancy.", "Second paragraph."]), 10_000)
    assert len(segments) == 1
    assert segments[0].page_number is None and segments[0].slide_number is None
    assert "Normalisation removes redundancy." in segments[0].text


def test_xlsx_reads_stored_values_only():
    segments = extract(FileType.XLSX, xlsx_bytes([["Term", "Meaning"], ["Key", "Unique identifier"]]), 10_000)
    assert "Unique identifier" in segments[0].text


def test_txt_and_character_budget():
    segments = extract(FileType.TXT, b"abcdefghij" * 100, 25)
    assert segments[0].text == ("abcdefghij" * 3)[:25]


def test_binary_data_is_not_text():
    with pytest.raises(ExtractionError):
        extract(FileType.TXT, b"\x00\x01binary", 1000)


@pytest.mark.parametrize("file_type", [FileType.PDF, FileType.DOCX, FileType.PPTX, FileType.XLSX])
def test_malformed_files_raise_extraction_errors(file_type):
    with pytest.raises(ExtractionError):
        extract(file_type, b"this is not a real file", 1000)


def test_xml_entities_and_doctypes_are_refused():
    bomb = ('<?xml version="1.0"?><!DOCTYPE d [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;">]>'
            '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
            '<w:body><w:p><w:r><w:t>&b;</w:t></w:r></w:p></w:body></w:document>')
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w") as package:
        package.writestr("word/document.xml", bomb)
    with pytest.raises(ExtractionError):
        extract(FileType.DOCX, out.getvalue(), 1000)


def test_oversized_package_members_are_refused(monkeypatch):
    from app.extraction import extractors

    monkeypatch.setattr(extractors, "MAX_XML_MEMBER_BYTES", 100)
    with pytest.raises(ExtractionError):
        extract(FileType.DOCX, docx_bytes(["word " * 100]), 10_000)


def test_chunks_are_bounded_overlapping_and_keep_location():
    text = " ".join(f"Sentence number {i} explains a database idea." for i in range(200))
    chunks = chunk_segments([Segment(text, page_number=4)], size=500, overlap=100)
    assert len(chunks) > 5
    assert all(len(chunk.text) <= 500 for chunk in chunks)
    assert all(chunk.page_number == 4 for chunk in chunks)
    # Consecutive chunks share text.
    assert chunks[0].text[-40:].split()[-1] in chunks[1].text
    # Deterministic.
    assert chunks == chunk_segments([Segment(text, page_number=4)], size=500, overlap=100)


def test_normalise_collapses_whitespace():
    assert normalise("a   b\t\tc\n\n\n\nd") == "a b c\n\nd"
