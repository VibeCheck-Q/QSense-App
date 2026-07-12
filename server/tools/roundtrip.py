"""Manual MQTT round-trip proof. Run the service (py -m server.main) first, in another terminal."""
import base64
import io
import json
import sys
import time
import uuid

from PIL import Image

from server.config import Config
from server.mqtt_service import make_client


def _sample_jpeg() -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (640, 480), (40, 60, 90)).save(buf, format="JPEG")
    return buf.getvalue()


def main() -> int:
    cfg = Config.default()
    request_id = str(uuid.uuid4())
    got: dict = {}

    def on_connect(c, u, f, rc, p):
        c.subscribe(cfg.response_topic, qos=cfg.qos)
        payload = json.dumps(
            {
                "requestId": request_id,
                "machineNo": "M1",
                "partNo": "blade-1",
                "imageB64": base64.b64encode(_sample_jpeg()).decode(),
                "timestamp": "2026-07-12T00:00:00Z",
            }
        ).encode()
        c.publish(cfg.request_topic, payload, qos=cfg.qos)
        print(f"published request {request_id}")

    def on_message(c, u, msg):
        data = json.loads(msg.payload)
        if data.get("requestId") == request_id:
            got.update(data)

    client = make_client(cfg)
    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(cfg.host, cfg.port, cfg.keepalive)
    client.loop_start()

    deadline = time.time() + 15
    while not got and time.time() < deadline:
        time.sleep(0.2)
    client.loop_stop()

    if not got:
        print("TIMEOUT — no response in 15s")
        return 1
    with open("server/data/annotated_out.jpg", "wb") as f:
        f.write(base64.b64decode(got["annotatedImageB64"]))
    print("diagnosis:", got["diagnosis"])
    print("saved server/data/annotated_out.jpg")
    return 0


if __name__ == "__main__":
    sys.exit(main())
