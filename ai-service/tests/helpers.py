"""Test helpers: sample sources, sample files built in memory, and request shortcuts."""

from __future__ import annotations

import base64
import io
import zipfile
from typing import TYPE_CHECKING

from app.config import Settings

if TYPE_CHECKING:
    from fastapi.testclient import TestClient

TOKEN = "test-internal-token-0123456789abcdef"
HEADERS = {"X-Stacc-AI-Token": TOKEN}
COURSE = "0123456789abcdef0123456789abcdef"
FINGERPRINT = "a" * 64
OTHER_FINGERPRINT = "b" * 64


def make_settings(tmp_path, **overrides) -> Settings:
    env = {"AI_PROVIDER": "fake", "STACC_AI_INTERNAL_TOKEN": TOKEN, "AI_INDEX_DIR": str(tmp_path / "indexes")}
    env.update(overrides)
    return Settings.from_env(env)


def topic(source_id: int, title: str, text: str) -> dict:
    return {"sourceType": "TOPIC", "sourceId": source_id, "topicId": source_id, "title": title, "text": text}


def material(source_id: int, topic_id: int, title: str, file_name: str, file_type: str, data: bytes) -> dict:
    return {"sourceType": "STUDY_MATERIAL", "sourceId": source_id, "topicId": topic_id, "title": title,
            "fileName": file_name, "fileType": file_type, "contentBase64": base64.b64encode(data).decode("ascii")}


def replace(client: TestClient, sources: list[dict], fingerprint: str = FINGERPRINT, course: str = COURSE):
    return client.post(f"/internal/indexes/{course}/replace", headers=HEADERS,
                       json={"contentFingerprint": fingerprint, "sources": sources})


# --- Small, valid files built in memory --------------------------------------------------------


def pdf_bytes(pages: list[str]) -> bytes:
    """A minimal PDF with one line of Helvetica text per page."""
    objects = ["<< /Type /Catalog /Pages 2 0 R >>"]
    kids = " ".join(f"{4 + 2 * i} 0 R" for i in range(len(pages)))
    objects.append(f"<< /Type /Pages /Kids [{kids}] /Count {len(pages)} >>")
    objects.append("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    for i, text in enumerate(pages):
        content = f"BT /F1 12 Tf 72 720 Td ({text}) Tj ET"
        objects.append(f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> "
                       f"/Contents {5 + 2 * i} 0 R >>")
        objects.append(f"<< /Length {len(content)} >>\nstream\n{content}\nendstream")
    out = io.BytesIO()
    out.write(b"%PDF-1.4\n")
    offsets = []
    for number, body in enumerate(objects, start=1):
        offsets.append(out.tell())
        out.write(f"{number} 0 obj\n{body}\nendobj\n".encode("latin-1"))
    xref = out.tell()
    out.write(f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode())
    for offset in offsets:
        out.write(f"{offset:010d} 00000 n \n".encode())
    out.write(f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
    return out.getvalue()


def _zip(parts: dict[str, str]) -> bytes:
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as package:
        for name, content in parts.items():
            package.writestr(name, content)
    return out.getvalue()


W_NS = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"'


def docx_bytes(paragraphs: list[str]) -> bytes:
    body = "".join(f"<w:p><w:r><w:t>{p}</w:t></w:r></w:p>" for p in paragraphs)
    return _zip({"word/document.xml": f'<?xml version="1.0"?><w:document {W_NS}><w:body>{body}</w:body></w:document>'})


def pptx_bytes(slides: list[str]) -> bytes:
    a = 'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
    p = 'xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"'
    r = 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'
    parts = {}
    ids, rels = "", ""
    # Stored as slide files in reverse, so the presentation order must come from presentation.xml.
    for number, text in enumerate(slides, start=1):
        file_number = len(slides) - number + 1
        parts[f"ppt/slides/slide{file_number}.xml"] = (
            f"<p:sld {p} {a}><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>{text}</a:t></a:r></a:p>"
            "</p:txBody></p:sp></p:spTree></p:cSld></p:sld>")
        ids += f'<p:sldId id="{255 + number}" r:id="rId{number}"/>'
        rels += f'<Relationship Id="rId{number}" Type="slide" Target="slides/slide{file_number}.xml"/>'
    parts["ppt/presentation.xml"] = f"<p:presentation {p} {r}><p:sldIdLst>{ids}</p:sldIdLst></p:presentation>"
    parts["ppt/_rels/presentation.xml.rels"] = (
        f'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">{rels}</Relationships>')
    return _zip(parts)


def xlsx_bytes(rows: list[list[str]]) -> bytes:
    s = 'xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"'
    shared = [value for row in rows for value in row]
    strings = "".join(f"<si><t>{value}</t></si>" for value in shared)
    cells, index = "", 0
    for row in rows:
        cells += "<row>" + "".join(f'<c t="s"><v>{index + i}</v></c>' for i in range(len(row))) + "</row>"
        index += len(row)
    return _zip({"xl/sharedStrings.xml": f"<sst {s}>{strings}</sst>",
                 "xl/worksheets/sheet1.xml": f"<worksheet {s}><sheetData>{cells}</sheetData></worksheet>"})
