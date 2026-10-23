param(
    [int[]]$Rates = @(100, 200, 300, 400, 500),
    [string]$Duration = "2m",
    [string]$RampDuration = "20s",
    [switch]$Allow1000,
    [switch]$PlanOnly,
    [int]$PreAllocatedVUs = 64,
    [int]$MaxVUs = 256,
    [string]$K6Memory = "512m",
    [string]$K6Cpus = "2",
    [string]$RequestTimeout = "5s",
    [int]$RecoveryTimeoutSeconds = 300,
    [int]$MaxOutboxBacklogGrowth = 5000,
    [double]$MinimumHostFreeMemoryGB = 3,
    [double]$MinimumDiskFreeGB = 20,
    [string]$BaseUrl = "http://frameworkjava-chat:18083",
    [string]$DockerNetwork = "frameworkjava_frameworkjava_network",
    [string]$MysqlContainer = "frameworkjava-mysql",
    [string]$MysqlUser = "appdev",
    [string]$MysqlPassword = "change-me",
    [string]$MysqlAdminUser = "root",
    [string]$MysqlAdminPassword = "change-me",
    [string]$MysqlDatabase = "frameworkjava_dev",
    [string[]]$ShardContainers = @("frameworkjava-mysql-shard-0", "frameworkjava-mysql-shard-1",
        "frameworkjava-mysql-shard-2", "frameworkjava-mysql-shard-3"),
    [string]$ShardUser = "appdev",
    [string]$ShardPassword = "change-me",
    [string]$ShardDatabase = "frameworkjava_dev",
    [int]$ShardsPerMysql = 8,
    [string]$StorageMode = "DUAL_WRITE",
    [string]$ChatContainer = "frameworkjava-chat",
    [string]$RabbitContainer = "frameworkjava-rabbitmq",
    [string]$RedisContainer = "frameworkjava-redis-master",
    [string]$RedisPassword = "change-me",
    [string]$OutputDirectory = ".agent/tasks/chat-production-hardening-v2/raw/baseline",
    [bool]$ProvisionFixtures = $true
)

$ErrorActionPreference = "Stop"
$Rates = @($Rates | Sort-Object -Unique)
if ($Rates.Count -eq 0 -or ($Rates | Where-Object { $_ -lt 1 -or $_ -gt 1000 })) {
    throw "Rates must be between 1 and 1000 msg/s"
}
if ($Rates -contains 1000 -and -not $Allow1000) {
    throw "The 1000 msg/s stage requires explicit -Allow1000 opt-in"
}
if ($PreAllocatedVUs -lt 1 -or $MaxVUs -lt $PreAllocatedVUs -or $MaxVUs -gt 256) {
    throw "Require 1 <= PreAllocatedVUs <= MaxVUs <= 256"
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$outputPath = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
    [IO.Path]::GetFullPath($OutputDirectory)
} else {
    [IO.Path]::GetFullPath((Join-Path $repoRoot $OutputDirectory))
}
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

function Write-JsonFile([string]$Path, [object]$Value) {
    $json = $Value | ConvertTo-Json -Depth 8
    [IO.File]::WriteAllText($Path, "$json`n", (New-Object Text.UTF8Encoding($false)))
}

$plan = [ordered]@{
    rates = $Rates
    duration = $Duration
    ramp_duration = $RampDuration
    pre_allocated_vus = $PreAllocatedVUs
    max_vus = $MaxVUs
    k6_memory = $K6Memory
    k6_cpus = $K6Cpus
    request_timeout = $RequestTimeout
    recovery_timeout_seconds = $RecoveryTimeoutSeconds
    max_outbox_backlog_growth = $MaxOutboxBacklogGrowth
    minimum_host_free_memory_gb = $MinimumHostFreeMemoryGB
    minimum_disk_free_gb = $MinimumDiskFreeGB
    stop_on_first_unhealthy_stage = $true
    requires_explicit_1000_opt_in = $true
}
Write-JsonFile (Join-Path $outputPath 'plan.json') $plan
if ($PlanOnly) {
    $plan | ConvertTo-Json -Depth 4
    exit 0
}

function Invoke-Mysql([string]$Sql) {
    $result = & docker exec -e "MYSQL_PWD=$MysqlPassword" $MysqlContainer `
        mysql "-u$MysqlUser" $MysqlDatabase --batch --raw --skip-column-names -e $Sql
    if ($LASTEXITCODE -ne 0) { throw "MySQL query failed" }
    return @($result)
}

function Invoke-MysqlAdmin([string]$Sql) {
    $result = & docker exec -e "MYSQL_PWD=$MysqlAdminPassword" $MysqlContainer `
        mysql "-u$MysqlAdminUser" $MysqlDatabase --batch --raw --skip-column-names -e $Sql
    if ($LASTEXITCODE -ne 0) { throw "MySQL admin diagnostic query failed" }
    return @($result)
}

function Invoke-MysqlIn([string]$Container, [string]$User, [string]$Password, [string]$Database, [string]$Sql) {
    $result = & docker exec -e "MYSQL_PWD=$Password" $Container `
        mysql "-u$User" $Database --batch --raw --skip-column-names -e $Sql
    if ($LASTEXITCODE -ne 0) { throw "MySQL query failed on $Container" }
    return @($result)
}

function ConvertTo-Seconds([string]$Duration) {
    if (-not $Duration) { return 0 }
    if ($Duration -match '^\d+$') { return [int]$Duration }
    $total = 0
    foreach ($m in [regex]::Matches($Duration, '(\d+)([smh])')) {
        $n = [int]$m.Groups[1].Value
        switch ($m.Groups[2].Value) {
            's' { $total += $n }
            'm' { $total += $n * 60 }
            'h' { $total += $n * 3600 }
        }
    }
    return $total
}

# Business-message rows: legacy table (main) plus every physical shard instance's
# chat_message_s% tables. In DUAL_WRITE the app's own writes are exactly the legacy
# delta; shard delta additionally includes any backfill rows, so both are recorded.
function Get-BusinessMessageSnapshot {
    $legacyValue = Invoke-Mysql "SELECT COUNT(*) FROM chat_message;"
    $legacy = [long]($legacyValue | Select-Object -First 1)
    $shards = 0L
    $perInstance = [Collections.Generic.List[long]]::new()
    for ($i = 0; $i -lt $ShardContainers.Count; $i++) {
        $firstShard = $i * $ShardsPerMysql
        $tables = @()
        for ($s = 0; $s -lt $ShardsPerMysql; $s++) {
            $tables += ('chat_message_s{0:d2}' -f ($firstShard + $s))
        }
        $union = ($tables | ForEach-Object { "SELECT COUNT(*) AS c FROM $_" }) -join ' UNION ALL '
        try {
            $value = Invoke-MysqlIn $ShardContainers[$i] $ShardUser $ShardPassword $ShardDatabase `
                "SELECT COALESCE(SUM(c),0) FROM ($union) t;"
            $count = [long]($value | Select-Object -First 1)
            if ($null -eq $count -or $count -lt 0) { $count = 0L }
            $shards += $count
            $perInstance.Add($count)
        } catch {
            Write-Warning "Shard row count failed on $($ShardContainers[$i]) : $($_.Exception.Message)"
            $perInstance.Add(-1L)
        }
    }
    return [ordered]@{ legacy = $legacy; shards = $shards; per_instance = @($perInstance) }
}

function Get-OutboxPending {
    $value = Invoke-Mysql "SELECT COUNT(*) FROM chat_message_outbox WHERE status IN (0,1);"
    return [long]($value | Select-Object -First 1)
}

function Test-ContainerHealthy([string]$Name) {
    $raw = & docker inspect $Name --format '{{json .State}}' 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $raw) { return $false }
    try {
        $state = $raw | ConvertFrom-Json
        return $state.Running -eq $true -and (!$state.Health -or $state.Health.Status -ne 'unhealthy')
    } catch {
        return $false
    }
}

function Test-CoreContainers {
    foreach ($container in @($ChatContainer, $MysqlContainer, $RabbitContainer, $RedisContainer)) {
        if (-not (Test-ContainerHealthy $container)) { return $false }
    }
    return $true
}

function Get-HostFreeMemoryGB {
    try {
        $os = Get-CimInstance Win32_OperatingSystem -ErrorAction Stop
        return [Math]::Round($os.FreePhysicalMemory / 1MB, 2)
    } catch {
        return $null
    }
}

function Assert-Preflight([string]$StageName) {
    if (-not (Test-CoreContainers)) { throw "Preflight $StageName failed: a core container is not healthy" }
    $runningK6 = @(& docker ps --filter ancestor=grafana/k6 --format '{{.ID}}')
    if ($LASTEXITCODE -ne 0) { throw "Preflight $StageName failed: Docker is unavailable" }
    if ($runningK6.Count -gt 0) { throw "Preflight $StageName failed: another k6 container is already running" }
    $drive = New-Object IO.DriveInfo(([IO.Path]::GetPathRoot($outputPath)))
    $freeDiskGB = [Math]::Round($drive.AvailableFreeSpace / 1GB, 2)
    if ($freeDiskGB -lt $MinimumDiskFreeGB) {
        throw "Preflight $StageName failed: only $freeDiskGB GB disk is free"
    }
    $freeMemoryGB = Get-HostFreeMemoryGB
    if ($null -eq $freeMemoryGB) { throw "Preflight $StageName failed: host free memory could not be measured" }
    if ($freeMemoryGB -lt $MinimumHostFreeMemoryGB) {
        throw "Preflight $StageName failed: only $freeMemoryGB GB host memory is free"
    }
    return [ordered]@{ free_disk_gb = $freeDiskGB; free_memory_gb = $freeMemoryGB }
}

function Save-Snapshot([string]$Name) {
    $errors = [Collections.Generic.List[string]]::new()
    try {
        Invoke-MysqlAdmin "SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_running','Threads_connected','Innodb_buffer_pool_wait_free','Innodb_buffer_pool_reads','Innodb_buffer_pool_read_requests','Innodb_log_waits','Innodb_row_lock_time','Innodb_row_lock_waits','Innodb_data_fsyncs','Innodb_os_log_written','Com_insert','Com_update','Questions');" |
            Set-Content -LiteralPath (Join-Path $outputPath "mysql-status-$Name.tsv") -Encoding utf8
        Invoke-MysqlAdmin "SELECT DIGEST_TEXT,COUNT_STAR,ROUND(SUM_TIMER_WAIT/1000000000,2),ROUND(AVG_TIMER_WAIT/1000000,2),SUM_ROWS_AFFECTED FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME='$MysqlDatabase' ORDER BY SUM_TIMER_WAIT DESC LIMIT 20;" |
            Set-Content -LiteralPath (Join-Path $outputPath "mysql-digests-$Name.tsv") -Encoding utf8
    } catch { $errors.Add("mysql: $($_.Exception.Message)") }
    try {
        & docker stats --no-stream --format '{{json .}}' $ChatContainer $MysqlContainer $RabbitContainer $RedisContainer |
            Set-Content -LiteralPath (Join-Path $outputPath "docker-stats-$Name.jsonl") -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw "docker stats failed" }
    } catch { $errors.Add("docker-stats: $($_.Exception.Message)") }
    try {
        & docker exec $RabbitContainer rabbitmqctl list_queues name messages_ready messages_unacknowledged --formatter json |
            Set-Content -LiteralPath (Join-Path $outputPath "rabbit-queues-$Name.json") -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw "RabbitMQ snapshot failed" }
    } catch { $errors.Add("rabbitmq: $($_.Exception.Message)") }
    try {
        & docker exec -e "REDISCLI_AUTH=$RedisPassword" $RedisContainer redis-cli INFO stats |
            Set-Content -LiteralPath (Join-Path $outputPath "redis-stats-$Name.txt") -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw "Redis snapshot failed" }
    } catch { $errors.Add("redis: $($_.Exception.Message)") }
    try {
        Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:18083/actuator/prometheus" -TimeoutSec 10 |
            Select-Object -ExpandProperty Content |
            Set-Content -LiteralPath (Join-Path $outputPath "prometheus-$Name.txt") -Encoding utf8
    } catch {
        $errors.Add("prometheus: $($_.Exception.Message)")
        "PROMETHEUS_UNAVAILABLE: $($_.Exception.Message)" |
            Set-Content -LiteralPath (Join-Path $outputPath "prometheus-$Name.txt") -Encoding utf8
    }
    try {
        # Invoke-RestMethod decodes the JSON body to an object; ConvertTo-Json re-serializes it.
        # Invoke-WebRequest on PowerShell 5.1 returns application/json Content as byte[], which
        # Set-Content wrote as decimal byte codes and broke the analyzer's JSON.parse.
        Invoke-RestMethod -Uri "http://127.0.0.1:18083/actuator/chatDataSourcePools" -TimeoutSec 10 |
            ConvertTo-Json -Depth 6 |
            Set-Content -LiteralPath (Join-Path $outputPath "datasource-pools-$Name.json") -Encoding utf8
    } catch {
        $errors.Add("datasource-pools: $($_.Exception.Message)")
        "DATASOURCE_POOLS_UNAVAILABLE: $($_.Exception.Message)" |
            Set-Content -LiteralPath (Join-Path $outputPath "datasource-pools-$Name.json") -Encoding utf8
    }
    return @($errors)
}

function Get-SummaryMetric([object]$Summary, [string]$MetricName, [string]$Key) {
    $metric = $Summary.metrics.$MetricName
    if ($null -eq $metric) { return $null }
    if ($metric.values -and $metric.values.PSObject.Properties[$Key]) {
        return [double]$metric.values.PSObject.Properties[$Key].Value
    }
    if ($metric.PSObject.Properties[$Key]) { return [double]$metric.PSObject.Properties[$Key].Value }
    if ($Key -eq 'rate' -and $metric.PSObject.Properties['value']) { return [double]$metric.value }
    return $null
}

function Test-StageSlo([string]$SummaryPath, [int]$Rate) {
    if (-not (Test-Path -LiteralPath $SummaryPath)) { return $false }
    try {
        $summary = Get-Content -LiteralPath $SummaryPath -Raw | ConvertFrom-Json
        $p95 = Get-SummaryMetric $summary 'http_req_duration' 'p(95)'
        $p99 = Get-SummaryMetric $summary 'http_req_duration' 'p(99)'
        $errorRate = Get-SummaryMetric $summary 'http_req_failed' 'rate'
        $dropped = Get-SummaryMetric $summary 'dropped_iterations' 'count'
        $achievedRate = Get-SummaryMetric $summary 'http_reqs' 'rate'
        return $null -ne $p95 -and $null -ne $p99 -and $null -ne $errorRate -and $null -ne $achievedRate `
            -and $p95 -lt 500 -and $p99 -lt 1200 -and $errorRate -lt 0.01 `
            -and ($null -eq $dropped -or $dropped -eq 0) -and $achievedRate -ge ($Rate * 0.95)
    } catch {
        return $false
    }
}

function Wait-Recovery([long]$PendingBefore) {
    $deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
    do {
        if (-not (Test-CoreContainers)) { return $false }
        try {
            $pending = Get-OutboxPending
            if ($pending -le ($PendingBefore + $MaxOutboxBacklogGrowth)) { return $true }
        } catch {
            return $false
        }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)
    return $false
}

$preflight = Assert-Preflight 'initial'
$maxRate = ($Rates | Measure-Object -Maximum).Maximum
$neededConversations = [Math]::Ceiling($maxRate / 40.0)
$targetRows = Invoke-Mysql "SELECT CONCAT(MIN(user_id), ':', conversation_id) FROM chat_conversation_member GROUP BY conversation_id HAVING COUNT(DISTINCT user_id) >= 2 ORDER BY conversation_id LIMIT $neededConversations;"
if ($targetRows.Count -lt $neededConversations -and $ProvisionFixtures) {
    $fixtureSql = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'chat-baseline-fixtures.sql') -Raw -Encoding utf8
    $fixtureSql | & docker exec -i -e "MYSQL_PWD=$MysqlPassword" $MysqlContainer mysql "-u$MysqlUser" $MysqlDatabase
    if ($LASTEXITCODE -ne 0) { throw "Failed to provision isolated chat baseline fixtures" }
    $targetRows = Invoke-Mysql "SELECT CONCAT(MIN(user_id), ':', conversation_id) FROM chat_conversation_member GROUP BY conversation_id HAVING COUNT(DISTINCT user_id) >= 2 ORDER BY conversation_id LIMIT $neededConversations;"
}
if ($targetRows.Count -lt $neededConversations) {
    throw "Need at least $neededConversations two-member conversations for $maxRate msg/s; found $($targetRows.Count)."
}

$chatTargets = ($targetRows -join ',')
$metadata = [ordered]@{
    started_at = (Get-Date).ToUniversalTime().ToString('o')
    planned_rates = $Rates
    rates = @()
    plan = $plan
    target_count = $targetRows.Count
    fixtures_provisioned = $ProvisionFixtures
    per_conversation_target_rate = [Math]::Ceiling($maxRate / $targetRows.Count)
    initial_preflight = $preflight
    git_head = (git rev-parse HEAD).Trim()
}
Write-JsonFile (Join-Path $outputPath 'metadata.json') $metadata
$beforeErrors = Save-Snapshot 'before'
if ($beforeErrors.Count -gt 0) { throw "Initial evidence snapshot failed: $($beforeErrors -join '; ')" }

$failed = $false
$executedRates = [Collections.Generic.List[int]]::new()
foreach ($rate in $Rates) {
    $stageStarted = (Get-Date).ToUniversalTime()
    $stagePreflight = Assert-Preflight "rate-$rate"
    $pendingBefore = Get-OutboxPending
    $businessBefore = Get-BusinessMessageSnapshot
    $summaryName = "summary-$rate.json"
    $summaryPath = Join-Path $outputPath $summaryName
    $logName = "k6-$rate.log"
    foreach ($stale in @($summaryPath, (Join-Path $outputPath "stage-$rate.json"))) {
        if (Test-Path -LiteralPath $stale) { Remove-Item -LiteralPath $stale -Force }
    }
    $mountPath = $outputPath -replace '\\','/'
    $repoMount = $repoRoot -replace '\\','/'
    $containerName = "anyujia-chat-baseline-$PID-$rate"
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & docker run --rm --name $containerName --network $DockerNetwork `
        --memory $K6Memory --cpus $K6Cpus --pids-limit 512 `
        -v "${repoMount}/deploy/perf:/scripts:ro" -v "${mountPath}:/results" `
        -e "BASE_URL=$BaseUrl" -e "RATE=$rate" -e "DURATION=$Duration" `
        -e "RAMP_DURATION=$RampDuration" -e "CHAT_TARGETS=$chatTargets" `
        -e "PRE_VUS=$PreAllocatedVUs" -e "MAX_VUS=$MaxVUs" -e "REQUEST_TIMEOUT=$RequestTimeout" `
        grafana/k6:latest run "--summary-export=/results/$summaryName" /scripts/chat-message-throughput-direct.js `
        2>&1 | Tee-Object -FilePath (Join-Path $outputPath $logName)
    $k6ExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorAction

    $executedRates.Add($rate)
    $metadata.rates = @($executedRates)
    Write-JsonFile (Join-Path $outputPath 'metadata.json') $metadata
    $snapshotErrors = Save-Snapshot "after-$rate"
    $containersHealthy = Test-CoreContainers
    $postflight = $null
    $postflightError = $null
    try {
        $postflight = Assert-Preflight "after-rate-$rate"
        $hostHealthy = $true
    } catch {
        $hostHealthy = $false
        $postflightError = $_.Exception.Message
    }
    $sloPassed = Test-StageSlo $summaryPath $rate
    $recoveryPassed = if ($containersHealthy -and $hostHealthy) { Wait-Recovery $pendingBefore } else { $false }
    $pendingAfter = try { Get-OutboxPending } catch { $null }
    $businessAfter = Get-BusinessMessageSnapshot
    $businessWrites = [long]($businessAfter.legacy) - [long]($businessBefore.legacy)
    $shardWrites = [long]($businessAfter.shards) - [long]($businessBefore.shards)
    # In SHARD_ONLY the app writes messages only to the physical shards; the legacy table
    # stops growing, so the business message count is the shard delta, not the legacy delta.
    if ($StorageMode -eq 'SHARD_ONLY') {
        $businessWrites = $shardWrites
    }
    $shardWritesPerInstance = @()
    $instanceCount = [Math]::Max($businessBefore.per_instance.Count, $businessAfter.per_instance.Count)
    for ($i = 0; $i -lt $instanceCount; $i++) {
        $piBefore = 0L
        $piAfter = 0L
        if ($i -lt $businessBefore.per_instance.Count -and [long]$businessBefore.per_instance[$i] -ge 0) { $piBefore = [long]$businessBefore.per_instance[$i] }
        if ($i -lt $businessAfter.per_instance.Count -and [long]$businessAfter.per_instance[$i] -ge 0) { $piAfter = [long]$businessAfter.per_instance[$i] }
        $shardWritesPerInstance += ($piAfter - $piBefore)
    }
    $steadySeconds = [Math]::Max(1, (ConvertTo-Seconds $Duration) - (ConvertTo-Seconds $RampDuration))
    $stage = [ordered]@{
        rate = $rate
        started_at = $stageStarted.ToString('o')
        finished_at = (Get-Date).ToUniversalTime().ToString('o')
        completed = (Test-Path -LiteralPath $summaryPath)
        k6_exit_code = $k6ExitCode
        slo_passed = $sloPassed
        containers_healthy_after = $containersHealthy
        host_healthy_after = $hostHealthy
        postflight = $postflight
        postflight_error = $postflightError
        recovery_passed = $recoveryPassed
        snapshot_errors = @($snapshotErrors)
        pending_outbox_before = $pendingBefore
        pending_outbox_after_recovery = $pendingAfter
        business_writes = $businessWrites
        shard_writes = $shardWrites
        shard_writes_per_instance = @($shardWritesPerInstance)
        business_steady_state_seconds = $steadySeconds
        business_msg_per_sec = [Math]::Round($businessWrites / $steadySeconds, 2)
        shard_msg_per_sec = [Math]::Round($shardWrites / $steadySeconds, 2)
        preflight = $stagePreflight
    }
    Write-JsonFile (Join-Path $outputPath "stage-$rate.json") $stage
    $stageHealthy = $stage.completed -and $sloPassed -and $containersHealthy -and $hostHealthy `
        -and $recoveryPassed -and $snapshotErrors.Count -eq 0
    if (-not $stageHealthy) {
        $failed = $true
        Write-Warning "Stopping after unhealthy $rate msg/s stage; no higher rate will be started."
        break
    }
}

& node (Join-Path $PSScriptRoot 'chat-baseline-analyze.js') $outputPath
if ($LASTEXITCODE -ne 0) { throw "Baseline analyzer failed" }
if ($failed) { exit 2 }
