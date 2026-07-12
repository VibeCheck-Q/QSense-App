import base64
import io
import json

from PIL import Image

from server.detector import StubDetector
from server.handler import handle_request


def _request_bytes(w=640, h=480, request_id="abc"):
    buf = io.BytesIO()
    Image.new("RGB", (w, h), (0, 0, 0)).save(buf, format="JPEG")
    b64 = base64.b64encode(buf.getvalue()).decode()
    return json.dumps(
        {
            "requestId": request_id,
            "machineNo": "M1",
            "partNo": "blade-1",
            "imageB64": b64,
            "timestamp": "2026-07-12T00:00:00Z",
        }
    ).encode()


def test_handle_request_returns_valid_response():
    out = json.loads(handle_request(_request_bytes(), StubDetector()))
    assert out["requestId"] == "abc"
    assert len(out["detections"]) == 1
    assert out["detections"][0]["cls"] == "blade"
    assert out["diagnosis"] == "Detected: 1 blade."
    img = Image.open(io.BytesIO(base64.b64decode(out["annotatedImageB64"])))
    assert img.format == "JPEG"


def test_handle_request_bad_payload_does_not_raise():
    out = json.loads(handle_request(b"not json", StubDetector()))
    assert out["detections"] == []
    assert "could not" in out["diagnosis"].lower()
