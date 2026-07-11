# Subscribes to the QSense outbound resolutions topic (Windows/PowerShell).
# Requires Mosquitto clients on PATH (mosquitto_sub). Leave running during the demo.
param(
    [string]$Broker = "test.mosquitto.org",
    [int]$Port = 1883,
    [string]$Topic = "qsense/machine/resolutions"
)

$topic = $Topic
Write-Host "Subscribing to $Broker`:$Port  topic=$topic  (Ctrl+C to stop)"
mosquitto_sub -h $Broker -p $Port -t $topic -q 1 -v
