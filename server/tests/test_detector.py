from server.detector import StubDetector


def test_stub_detector_returns_one_centered_blade_box():
    dets = StubDetector().infer(width=640, height=480)
    assert len(dets) == 1
    d = dets[0]
    assert d.cls == "blade"
    assert d.score == 1.0
    x1, y1, x2, y2 = d.box
    assert (x1, y1, x2, y2) == (160, 120, 480, 360)
