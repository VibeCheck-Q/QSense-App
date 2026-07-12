import json
from types import SimpleNamespace

from server.config import Config
from server.detector import StubDetector
from server.mqtt_service import VisionService
from server.tests.test_handler import _request_bytes


class FakeClient:
    def __init__(self):
        self.published = []

    def publish(self, topic, payload, qos):
        self.published.append((topic, payload, qos))


def test_on_message_publishes_response_to_response_topic():
    svc = VisionService(Config.default(), StubDetector())
    svc.client = FakeClient()
    msg = SimpleNamespace(payload=_request_bytes(request_id="xyz"))
    svc._on_message(svc.client, None, msg)
    assert len(svc.client.published) == 1
    topic, payload, qos = svc.client.published[0]
    assert topic == "qsense/vision/response"
    assert qos == 1
    assert json.loads(payload)["requestId"] == "xyz"
