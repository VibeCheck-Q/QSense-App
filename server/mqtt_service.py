import logging

import paho.mqtt.client as mqtt

from server.config import Config
from server.detector import Detector
from server.handler import handle_request

log = logging.getLogger("qsense.vision")


def make_client(config: Config) -> mqtt.Client:
    client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION2, transport=config.transport, protocol=mqtt.MQTTv5
    )
    if config.transport == "websockets":
        client.ws_set_options(path=config.ws_path)
    return client


class VisionService:
    def __init__(self, config: Config, detector: Detector):
        self.config = config
        self.detector = detector
        self.client = make_client(config)

    def _on_connect(self, client, userdata, flags, reason_code, properties):
        log.info("connected rc=%s; subscribing %s", reason_code, self.config.request_topic)
        client.subscribe(self.config.request_topic, qos=self.config.qos)

    def _on_message(self, client, userdata, msg):
        try:
            response = handle_request(msg.payload, self.detector)
            client.publish(self.config.response_topic, response, qos=self.config.qos)
            log.info("responded (%d bytes)", len(response))
        except Exception:  # defensive: the loop must survive any message
            log.exception("failed handling message")

    def run(self) -> None:
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self.client.connect(self.config.host, self.config.port, self.config.keepalive)
        self.client.loop_forever()
