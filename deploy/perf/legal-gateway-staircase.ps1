param(
    [string]$BaseUrl = 'http://127.0.0.1:18090',
    [string]$Token = $env:LEGAL_PERF_TOKEN,
    [string]$K6Image = 'grafana/k6:latest',
    [string]$K6Network = 'frameworkjava_frameworkjava_network',
    [int[]]$Rates = @(200, 250, 300),
    [string]$Duration = '30s',
    [string]$OutputDirectory = 'deploy/perf/results'
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Token)) {
    throw '必须通过 -Token 或 LEGAL_PERF_TOKEN 提供临时压测 Token。'
}

$repoRoot = (Get-Location).Path
$scriptPath = Join-Path $repoRoot 'deploy/perf/legal-agent-load.js'
$outputRoot = Join-Path $repoRoot $OutputDirectory
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null

function Invoke-K6Stage([int]$Rate) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $outputFile = Join-Path $outputRoot ("legal-agent-gateway-staircase-$Rate-$stamp.json")
    $containerOutput = "/work/$OutputDirectory/$(Split-Path $outputFile -Leaf)"
    $mountRoot = ($repoRoot -replace '\\', '/')

    Write-Host "开始阶梯档位：$Rate req/s，持续 $Duration"
    & docker run --rm --network $K6Network `
        -v "${mountRoot}:/work" `
        -e "BASE_URL=$BaseUrl" `
        -e "TOKEN=$Token" `
        -e "ENQUEUE_RATE=$Rate" `
        -e "ENQUEUE_DURATION=$Duration" `
        -e 'SCENARIO=enqueue' `
        $K6Image run --summary-export $containerOutput /work/deploy/perf/legal-agent-load.js

    if ($LASTEXITCODE -ne 0) {
        Write-Warning "k6 档位 $Rate 返回非零状态；保留结果文件继续分析。"
    }
}

foreach ($rate in $Rates) {
    Invoke-K6Stage $rate
}

Write-Host "阶梯压测完成，结果目录：$outputRoot"
