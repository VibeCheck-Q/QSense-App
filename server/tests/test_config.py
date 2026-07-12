from server.config import Config


def test_default_config_matches_broker_and_topics():
    c = Config.default()
    assert c.host == "test.mosquitto.org"
    assert c.port == 8080
    assert c.request_topic == "qsense/vision/request"
    assert c.response_topic == "qsense/vision/response"
    assert c.qos == 1
    assert c.transport == "websockets"
    assert c.ws_path == "/mqtt"
