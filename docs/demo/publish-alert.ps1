# Publishes a sample fault alert to the QSense inbound topic (Windows/PowerShell).
# Requires Mosquitto clients on PATH (mosquitto_pub).
param(
    [string]$Broker = "test.mosquitto.org",
    [int]$Port = 1883,
    [string]$Topic = "qsense/machine/monitoring"
)

$payload = '{"alertId":"e2d69c69-f6a6-4850-b76b-7912fc491e61","machineNo":"M-01","partName":"Fan Motor","partNo":"PN-001","severity":48.896,"timestamp":"2026-07-11T17:57:05.435079"}'
$topic = $Topic

Write-Host "Publishing to $Broker`:$Port  topic=$topic"
mosquitto_pub -h $Broker -p $Port -t $topic -q 1 -m $payload
