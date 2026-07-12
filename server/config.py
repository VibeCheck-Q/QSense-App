from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    host: str
    port: int
    request_topic: str
    response_topic: str
    keepalive: int
    qos: int
    transport: str  # "tcp" or "websockets"
    ws_path: str  # websocket path, used only when transport == "websockets"

    @classmethod
    def default(cls) -> "Config":
        # Defaults to MQTT-over-WebSocket (:8080 /mqtt): raw MQTT on :1883 is silently blocked by
        # some networks (TCP connects but no CONNACK) — mirrors the app's useWebSocket fallback.
        return cls(
            host="test.mosquitto.org",
            port=8080,
            request_topic="qsense/vision/request",
            response_topic="qsense/vision/response",
            keepalive=60,
            qos=1,
            transport="websockets",
            ws_path="/mqtt",
        )
