# QSense v1 — Demo Runbook

End-to-end demo of the operator app: an MQTT fault alert arrives, the on-device GenieX LLM
generates likely causes + fixes, the operator resolves one, and the acknowledgement is
published back over MQTT.

## Prerequisites
- A supported Snapdragon phone (Snapdragon 8 Elite `SM8750` or 8 Elite Gen 5 `SM8850`),
  developer mode + `adb` enabled.
- Mosquitto clients (`mosquitto_pub` / `mosquitto_sub`) on your machine.
- A Qualcomm AI Hub / GGUF instruct-LLM bundle to side-load.

Default broker is the public `test.mosquitto.org:1883` and topic namespace `qsense-demo`.
This broker is **insecure** — do not send real factory data. Override host/port/namespace via
the script parameters, and set the namespace to a team-unique value to avoid collisions.

## Steps

1. **Side-load the model** to the exact path the app reads
   (`getExternalFilesDir(null)/models/qsense-llm`):
   ```
   adb push <your-bundle> /sdcard/Android/data/com.example.qsense/files/models/qsense-llm
   ```
   If the app shows `Model: Model not found at …`, push to the path it prints.

2. **Watch resolutions** in one terminal (leave running):
   ```
   # Windows
   ./watch-resolutions.ps1
   # macOS/Linux
   ./watch-resolutions.sh
   ```

3. **Run the app** on the phone (`./gradlew :androidApp:installDebug` or Run in Android
   Studio). Wait for `Model: ready` and `MQTT: connected`.

4. **Publish a fault alert** in a second terminal:
   ```
   # Windows
   ./publish-alert.ps1
   # macOS/Linux
   ./publish-alert.sh
   ```

5. In the app: the alert appears → tap it → 3–5 causes + fixes are generated → pick the actual
   cause, add notes → **Resolve**. The resolution JSON appears in the `watch-resolutions`
   terminal.

## Custom broker / namespace
```
./publish-alert.ps1 -Broker 192.168.1.10 -Port 1883 -Namespace team7
./publish-alert.sh   192.168.1.10 1883 team7
```
Set the same host/port/namespace in the app via `MqttConfig` (see
`shared/src/androidMain/.../di/AndroidConfig.kt`).
