import io

from PIL import Image, ImageDraw

from server.detector import Detection


def annotate(image_bytes: bytes, detections: list[Detection]) -> bytes:
    img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    draw = ImageDraw.Draw(img)
    for d in detections:
        x1, y1, x2, y2 = d.box
        draw.rectangle([x1, y1, x2, y2], outline=(255, 92, 74), width=3)
        draw.text((x1 + 4, y1 + 4), f"{d.cls} {d.score:.2f}", fill=(255, 255, 255))
    out = io.BytesIO()
    img.save(out, format="JPEG", quality=85)
    return out.getvalue()
