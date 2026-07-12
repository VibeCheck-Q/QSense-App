from server.detector import Detection
from server.diagnose import diagnose


def test_diagnose_empty():
    assert diagnose([]) == "No objects detected."


def test_diagnose_counts_by_class():
    dets = [Detection("blade", 1.0, (0, 0, 1, 1)), Detection("disturbance", 0.8, (0, 0, 1, 1))]
    assert diagnose(dets) == "Detected: 1 blade, 1 disturbance."
