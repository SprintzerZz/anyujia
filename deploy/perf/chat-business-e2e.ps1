param(
    [string]$WebSocketUrl = "ws://127.0.0.1:18083/chat/websocket",
    [string]$HttpBaseUrl = "http://127.0.0.1:18083",
    [long]$SessionId = 900000000000100001,
    [long]$SenderId = 1,
    [long]$RecipientId = 2,
    [string]$MysqlContainer = "frameworkjava-mysql",
    [string]$MysqlUser = "appdev",
    [string]$MysqlPassword = "change-me",
    [string]$MysqlDatabase = "frameworkjava_dev",
    [string[]]$ShardContainers = @("frameworkjava-mysql-shard-0", "frameworkjava-mysql-shard-1",
        "frameworkjava-mysql-shard-2", "frameworkjava-mysql-shard-3"),
    [string]$ShardUser = "root",
    [string]$ShardPassword = "change-me",
    [string]$ShardDatabase = "frameworkjava_dev",
    [int]$ShardsPerMysql = 8,
    [string]$RedisContainer = "frameworkjava-redis-master",
    [string]$RedisPassword = "change-me",
    [string]$RabbitApi = "http://127.0.0.1:15672",
    [string]$RabbitUser = "admin",
    [string]$RabbitPassword = "change-me",
    [int]$TimeoutSeconds = 30
)

$ErrorActionPreference = "Stop"

function Invoke-DockerText([string]$Container, [string[]]$Arguments) {
    $output = & docker exec $Container @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) { throw "docker exec failed: $Container $($Arguments -join ' ')" }
    return ($output -join "`n").Trim()
}

function Invoke-MysqlScalar([string]$Sql) {
    $output = & docker exec -e "MYSQL_PWD=$MysqlPassword" $MysqlContainer `
        mysql "-u$MysqlUser" $MysqlDatabase -Nse $Sql
    if ($LASTEXITCODE -ne 0) { throw "MySQL query failed" }
    return ($output -join "`n").Trim()
}

function Invoke-ShardMysqlScalar([string]$Container, [string]$Sql) {
    $output = & docker exec -e "MYSQL_PWD=$ShardPassword" $Container `
        mysql "-u$ShardUser" $ShardDatabase -Nse $Sql
    if ($LASTEXITCODE -ne 0) { throw "Shard MySQL query failed: $Container" }
    return ($output -join "`n").Trim()
}

function Get-MessageId([string]$ClientMessageId) {
    $legacyId = Invoke-MysqlScalar "SELECT id FROM chat_message WHERE sender_id=$SenderId AND client_message_id='$ClientMessageId' LIMIT 1;"
    if ($legacyId) { return $legacyId }

    # SHARD_ONLY deployments do not populate the legacy table. Mirror
    # ChatMessageShardRouter: virtual_bucket = conversation_id % 1024,
    # physical_shard = virtual_bucket % physical_shard_count.
    $physicalShardCount = $ShardContainers.Count * $ShardsPerMysql
    $virtualBucket = $SessionId % 1024
    $physicalShard = $virtualBucket % $physicalShardCount
    $mysqlIndex = [int]($physicalShard / $ShardsPerMysql)
    $table = "chat_message_s{0:d2}" -f $physicalShard
    return Invoke-ShardMysqlScalar $ShardContainers[$mysqlIndex] `
        "SELECT id FROM $table WHERE sender_id=$SenderId AND client_message_id='$ClientMessageId' LIMIT 1;"
}

function Invoke-Redis([string[]]$Arguments) {
    $output = & docker exec -e "REDISCLI_AUTH=$RedisPassword" $RedisContainer redis-cli --raw @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Redis command failed" }
    return ($output -join "`n").Trim()
}

function Wait-Until([string]$Name, [scriptblock]$Probe) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $value = & $Probe
        if ($null -ne $value -and $value -ne "" -and $value -ne "0") {
            Write-Host "PASS $Name"
            return $value
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Name"
}

function Receive-WebSocketText([System.Net.WebSockets.ClientWebSocket]$Socket, [int]$Milliseconds) {
    $buffer = New-Object byte[] 65536
    $segment = [ArraySegment[byte]]::new($buffer)
    $cancel = [Threading.CancellationTokenSource]::new($Milliseconds)
    try {
        $result = $Socket.ReceiveAsync($segment, $cancel.Token).GetAwaiter().GetResult()
        if ($result.MessageType -eq [Net.WebSockets.WebSocketMessageType]::Close) { return $null }
        $stream = [IO.MemoryStream]::new()
        try {
            $stream.Write($buffer, 0, $result.Count)
            while (-not $result.EndOfMessage) {
                $result = $Socket.ReceiveAsync($segment, $cancel.Token).GetAwaiter().GetResult()
                $stream.Write($buffer, 0, $result.Count)
            }
            return [Text.Encoding]::UTF8.GetString($stream.ToArray())
        } finally { $stream.Dispose() }
    } catch [OperationCanceledException] { return $null }
    finally { $cancel.Dispose() }
}

$memberCount = Invoke-MysqlScalar "SELECT COUNT(*) FROM chat_conversation_member WHERE conversation_id=$SessionId AND user_id IN ($SenderId,$RecipientId);"
if ([int]$memberCount -ne 2) { throw "Session $SessionId does not contain sender and recipient" }
Write-Host "PASS conversation membership"

$headers = @{ userId = "$RecipientId"; userFrom = "app"; "Content-Type" = "application/json" }
Invoke-RestMethod -Method Post -Uri "$HttpBaseUrl/chat/session/list" -Headers $headers -Body '{"count":20}' | Out-Null
$headers.userId = "$SenderId"
Invoke-RestMethod -Method Post -Uri "$HttpBaseUrl/chat/session/list" -Headers $headers -Body '{"count":20}' | Out-Null

$rabbitCredential = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${RabbitUser}:${RabbitPassword}"))
$rabbitHeaders = @{ Authorization = "Basic $rabbitCredential" }
$exchangeUrl = "$RabbitApi/api/exchanges/%2F/chat.message.persist.exchange"
$exchangeBefore = Invoke-RestMethod -Uri $exchangeUrl -Headers $rabbitHeaders
$publishedBefore = if ($exchangeBefore.message_stats) { [long]$exchangeBefore.message_stats.publish_in } else { 0L }

$requestId = "e2e-$([Guid]::NewGuid().ToString('N'))"
$content = "business-e2e-$requestId"
$frame = @{
    version = 1
    requestId = $requestId
    type = "chat"
    data = @{ sessionId = $SessionId; clientMessageId = $requestId; type = 0; content = $content }
} | ConvertTo-Json -Compress -Depth 4

$socket = [Net.WebSockets.ClientWebSocket]::new()
$socket.Options.SetRequestHeader("userId", "$SenderId")
$socket.Options.SetRequestHeader("userFrom", "app")
try {
    [void]$socket.ConnectAsync([Uri]$WebSocketUrl, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
    Write-Host "PASS websocket handshake"
    $bytes = [Text.Encoding]::UTF8.GetBytes($frame)
    [void]$socket.SendAsync([ArraySegment[byte]]::new($bytes),
        [Net.WebSockets.WebSocketMessageType]::Text, $true,
        [Threading.CancellationToken]::None).GetAwaiter().GetResult()

    $accepted = $false
    $persistedAck = $false
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline -and (-not $accepted -or -not $persistedAck)) {
        $remaining = [Math]::Max(1000, [int]($deadline - [DateTime]::UtcNow).TotalMilliseconds)
        $text = Receive-WebSocketText $socket $remaining
        if (-not $text) { continue }
        $received = $text | ConvertFrom-Json
        if ($received.requestId -eq $requestId -and $received.type -eq "chat_accepted") { $accepted = $true }
        if ($received.requestId -eq $requestId -and $received.type -eq "chat_ack") { $persistedAck = $true }
        if ($received.type -eq "error") { throw "Chat protocol error: $text" }
    }
    if (-not $accepted) { throw "chat_accepted was not received" }
    if (-not $persistedAck) { throw "afterCommit chat_ack was not received" }
    Write-Host "PASS publisher-confirm accepted ACK"
    Write-Host "PASS MySQL afterCommit persistence ACK"
} finally {
    if ($socket.State -eq [Net.WebSockets.WebSocketState]::Open) {
        [void]$socket.CloseAsync([Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "done",
            [Threading.CancellationToken]::None).GetAwaiter().GetResult()
    }
    $socket.Dispose()
}

$messageId = Wait-Until "MySQL chat_message persistence" {
    Get-MessageId $requestId
}

Wait-Until "RabbitMQ persistent exchange publish" {
    $exchange = Invoke-RestMethod -Uri $exchangeUrl -Headers $rabbitHeaders
    if ($exchange.message_stats -and [long]$exchange.message_stats.publish_in -gt $publishedBefore) { "1" } else { "0" }
} | Out-Null

Wait-Until "Outbox derived events completed" {
    Invoke-MysqlScalar "SELECT IF(COUNT(*)=4 AND SUM(status=2)=4,1,0) FROM chat_message_outbox WHERE message_id=$messageId AND event_type IN ('recipient_push','unread_refresh','conversation_index');"
} | Out-Null

$unread = Wait-Until "Redis unread count" {
    $value = Invoke-Redis @("HGET", "chat:unread:$RecipientId", "$SessionId")
    if ($value -match '^\d+$' -and [long]$value -ge 1) { $value } else { "0" }
}

Wait-Until "Redis sender conversation list" {
    $values = Invoke-Redis @("LRANGE", "chat:conversation:list:$SenderId", "0", "-1")
    if ($values -match [regex]::Escape("$messageId")) { "1" } else { "0" }
} | Out-Null
Wait-Until "Redis recipient conversation list" {
    $values = Invoke-Redis @("LRANGE", "chat:conversation:list:$RecipientId", "0", "-1")
    if ($values -match [regex]::Escape("$messageId")) { "1" } else { "0" }
} | Out-Null

Write-Host "Business E2E passed: requestId=$requestId messageId=$messageId unread=$unread"
