import logging

from server.config import Config
from server.detector import StubDetector
from server.mqtt_service import VisionService


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    VisionService(Config.default(), StubDetector()).run()


if __name__ == "__main__":
    main()
