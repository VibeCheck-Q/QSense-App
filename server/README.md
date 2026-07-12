# QSense Vision Service (PC)

Receives images over MQTT, returns an annotated image + diagnosis. Slice A uses a stub detector
(no ML) — its only job is to prove the send/receive pipeline over MQTT works.

## Run

Run everything from the **repo root** (so `server` imports resolve):

```
py -m pip install -r server/requirements.txt
py -m server.main               # terminal 1: the service (subscribes qsense/vision/request)
py -m server.tools.roundtrip    # terminal 2: publishes a test image, saves annotated_out.jpg
```

`roundtrip` should print `diagnosis: Detected: 1 blade.` and write `server/data/annotated_out.jpg`.

## Tests

```
py -m pytest server/tests -v
```

## Config

Broker/topics live in `config.py` (defaults match the QSense app's `MqttConfig`:
`test.mosquitto.org:1883`, topics `qsense/vision/{request,response}`).

> ⚠️ `test.mosquitto.org` is a **public** broker and these topics are world-readable. Fine for this
> plumbing proof; use a private broker before sending real images.
