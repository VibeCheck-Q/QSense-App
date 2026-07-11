# Publishes a sample fault alert to the QSense inbound topic (Windows/PowerShell).
# Requires Mosquitto clients on PATH (mosquitto_pub).
param(
    [string]$Broker = "test.mosquitto.org",
    [int]$Port = 1883,
    [string]$Namespace = "qsense-demo"
)

$payload = '{"alertId":"a1b2c3","machineNo":"MTR-07","partName":"Blade","partNo":"BLD-330","severity":"high","timestamp":"2026-07-11T10:30:00Z","temperature":78,"humidity":82}'
$topic = "qsense/$Namespace/alerts"

Write-Host "Publishing to $Broker`:$Port  topic=$topic"
mosquitto_pub -h $Broker -p $Port -t $topic -q 1 -m $payload
