import io

from PIL import Image

from server.annotate import annotate
from server.detector import Detection


def _jpeg(w, h):
    buf = io.BytesIO()
    Image.new("RGB", (w, h), (10, 20, 30)).save(buf, format="JPEG")
    return buf.getvalue()


def test_annotate_returns_valid_jpeg_same_size():
    src = _jpeg(640, 480)
    out = annotate(src, [Detection("blade", 1.0, (10, 10, 100, 100))])
    img = Image.open(io.BytesIO(out))
    assert img.format == "JPEG"
    assert img.size == (640, 480)
