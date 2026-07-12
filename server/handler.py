import base64
import io
import json

from PIL import Image

from server.annotate import annotate
from server.detector import Detector
from server.diagnose import diagnose


def handle_request(payload: bytes, detector: Detector) -> bytes:
    request_id = ""
    try:
        req = json.loads(payload)
        request_id = req.get("requestId", "")
        image_bytes = base64.b64decode(req["imageB64"])
        width, height = Image.open(io.BytesIO(image_bytes)).size
        detections = detector.infer(width, height)
        annotated = annotate(image_bytes, detections)
        response = {
            "requestId": request_id,
            "annotatedImageB64": base64.b64encode(annotated).decode(),
            "detections": [
                {"cls": d.cls, "score": d.score, "box": list(d.box)} for d in detections
            ],
            "diagnosis": diagnose(detections),
        }
    except Exception as exc:  # never let a bad message crash the loop
        response = {
            "requestId": request_id,
            "annotatedImageB64": "",
            "detections": [],
            "diagnosis": f"Could not process image: {exc}",
        }
    return json.dumps(response).encode()
