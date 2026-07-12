from collections import Counter

from server.detector import Detection


def diagnose(detections: list[Detection]) -> str:
    if not detections:
        return "No objects detected."
    counts = Counter(d.cls for d in detections)
    parts = [f"{n} {cls}" for cls, n in counts.items()]
    return "Detected: " + ", ".join(parts) + "."
