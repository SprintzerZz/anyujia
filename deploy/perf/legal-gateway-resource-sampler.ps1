param(
    [string]$GatewayUrl = 'http://127.0.0.1:18090',
    [string]$GatewayContainer = 'gateway-load-check',
    [string]$LegalContainer = 'legal-load-check',
    [int]$DurationSeconds = 30,
    [int]$IntervalMilliseconds = 1000,
    [string]$OutputPath = 'deploy/perf/results/legal-agent-gateway-resource-sample.json'
)

$ErrorActionPreference = 'Stop'
$samples = [System.Collections.Generic.List[object]]::new()
$started = Get-Date
$endAt = $started.AddSeconds($DurationSeconds)

function Get-MetricValue([string]$Name) {
    try {
        $json = & curl.exe -s --fail "$GatewayUrl/actuator/metrics/$Name"
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($json)) { return $null }
        $metric = $json | ConvertFrom-Json
        $value = $metric.measurements | Where-Object { $_.statistic -eq 'VALUE' } | Select-Object -First 1
        if ($null -eq $value) { return $null }
        return [double]$value.value
    } catch {
        return $null
    }
}

function Get-ContainerResource([string]$Name) {
    try {
        $json = & docker stats --no-stream --format '{{json .}}' $Name
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($json)) { return $null }
        $item = $json | ConvertFrom-Json
        $cpu = [double](($item.CPUPerc -replace '%', '').Trim())
        $memory = (($item.MemUsage -split '\s*/')[0]).Trim()
        return [pscustomobject]@{ cpuPercent = $cpu; memory = $memory }
    } catch {
        return $null
    }
}

while ((Get-Date) -lt $endAt) {
    $now = Get-Date
    $gatewayResource = Get-ContainerResource $GatewayContainer
    $legalResource = Get-ContainerResource $LegalContainer
    $samples.Add([pscustomobject]@{
        timestamp = $now.ToString('o')
        elapsedSeconds = [math]::Round(($now - $started).TotalSeconds, 3)
        connectionPoolActive = Get-MetricValue 'reactor.netty.connection.provider.active.connections'
        connectionPoolPending = Get-MetricValue 'reactor.netty.connection.provider.pending.connections'
        gatewayForwardActive = Get-MetricValue 'gateway.forward.active'
        gatewayCpuPercent = if ($null -eq $gatewayResource) { $null } else { $gatewayResource.cpuPercent }
        gatewayMemory = if ($null -eq $gatewayResource) { $null } else { $gatewayResource.memory }
        legalCpuPercent = if ($null -eq $legalResource) { $null } else { $legalResource.cpuPercent }
        legalMemory = if ($null -eq $legalResource) { $null } else { $legalResource.memory }
    })
    Start-Sleep -Milliseconds $IntervalMilliseconds
}

$directory = Split-Path -Parent $OutputPath
if ($directory -and -not (Test-Path $directory)) { New-Item -ItemType Directory -Path $directory | Out-Null }
$samples | ConvertTo-Json -Depth 5 | Set-Content -Path $OutputPath -Encoding UTF8
$samples | ConvertTo-Json -Depth 5
