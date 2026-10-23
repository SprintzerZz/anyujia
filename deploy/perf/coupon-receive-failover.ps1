param(
    [int]$FailoverAfterSeconds = 30,
    [string]$RedisMasterContainer = "frameworkjava-redis-master",
    [string]$K6Script = ".\coupon-receive-failover.js"
)

$ErrorActionPreference = "Stop"

Write-Host "Starting k6 coupon receive failover test..."
$k6 = Start-Process -FilePath "k6" -ArgumentList @("run", $K6Script) -NoNewWindow -PassThru

try {
    Start-Sleep -Seconds $FailoverAfterSeconds
    Write-Host "Stopping Redis master container: $RedisMasterContainer"
    docker stop $RedisMasterContainer | Out-Host
    Wait-Process -Id $k6.Id
} finally {
    if (!$k6.HasExited) {
        Stop-Process -Id $k6.Id -Force
    }
}
