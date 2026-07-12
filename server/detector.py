from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Detection:
    cls: str
    score: float
    box: tuple[int, int, int, int]  # x1, y1, x2, y2


class Detector(Protocol):
    def infer(self, width: int, height: int) -> list[Detection]:
        ...


class StubDetector:
    """Placeholder for Slice A — returns a fixed box over the centre half of the frame."""

    def infer(self, width: int, height: int) -> list[Detection]:
        x1, y1 = width // 4, height // 4
        x2, y2 = width - x1, height - y1
        return [Detection(cls="blade", score=1.0, box=(x1, y1, x2, y2))]
