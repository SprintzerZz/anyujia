param(
    [string]$MysqlContainer = "frameworkjava-mysql",
    [string]$RedisContainer = "frameworkjava-redis",
    [string]$RabbitContainer = "frameworkjava-rabbitmq",
    [string]$ChatHealthUrl = "http://127.0.0.1:18083/actuator/health",
    [string]$MysqlUser = "appdev",
    [string]$MysqlPassword = "change-me",
    [string]$MysqlDatabase = "frameworkjava_dev",
    [string]$RedisPassword = "change-me"
)

$ErrorActionPreference = "Stop"

function Invoke-DockerText([string]$Container, [string[]]$Arguments) {
    $output = & docker exec $Container @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker exec failed: $Container $($Arguments -join ' ')"
    }
    return ($output -join "`n").Trim()
}

function Assert-Equal([string]$Name, [string]$Expected, [string]$Actual) {
    if ($Expected -ne $Actual) {
        throw "$Name expected '$Expected' but got '$Actual'"
    }
    Write-Host "PASS $Name"
}

$containers = @($MysqlContainer, $RedisContainer, $RabbitContainer, "frameworkjava-chat")
foreach ($container in $containers) {
    $running = docker inspect --format '{{.State.Running}}' $container 2>$null
    Assert-Equal "container $container running" "true" ($running.Trim())
}

$tableCount = Invoke-DockerText $MysqlContainer @(
    "mysql", "-u$MysqlUser", "-p$MysqlPassword", $MysqlDatabase,
    "-Nse", "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'chat_message_outbox';"
)
Assert-Equal "outbox table" "1" $tableCount

$cleanupIndex = Invoke-DockerText $MysqlContainer @(
    "mysql", "-u$MysqlUser", "-p$MysqlPassword", $MysqlDatabase,
    "-Nse", "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'chat_message_outbox' AND index_name = 'idx_chat_outbox_sent_cleanup';"
)
Assert-Equal "outbox cleanup index" "1" $cleanupIndex

$redisKey = "chat:e2e:smoke:$([guid]::NewGuid().ToString('N'))"
$redisSet = Invoke-DockerText $RedisContainer @("redis-cli", "-a", $RedisPassword, "SET", $redisKey, "ok", "EX", "30")
Assert-Equal "redis SET" "OK" $redisSet
$redisGet = Invoke-DockerText $RedisContainer @("redis-cli", "-a", $RedisPassword, "GET", $redisKey)
Assert-Equal "redis GET" "ok" $redisGet
Invoke-DockerText $RedisContainer @("redis-cli", "-a", $RedisPassword, "DEL", $redisKey) | Out-Null

$queueOutput = Invoke-DockerText $RabbitContainer @("rabbitmqctl", "list_queues", "name", "durable", "messages")
if ($queueOutput -notmatch "chat\.message\.persist\.queue\s+true") {
    throw "RabbitMQ persistence queue is missing or not durable"
}
if ($queueOutput -notmatch "chat\.message\.persist\.dlq\s+true") {
    throw "RabbitMQ persistence DLQ is missing or not durable"
}
Write-Host "PASS rabbit persistence queue and DLQ"

$health = Invoke-WebRequest -Uri $ChatHealthUrl -UseBasicParsing
if ($health.StatusCode -ne 200) {
    throw "chat health endpoint returned HTTP $($health.StatusCode)"
}
Write-Host "PASS chat health endpoint"

Write-Host "Chat infrastructure E2E smoke test passed."
