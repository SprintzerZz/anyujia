param(
    [int]$MessageCount = 30,
    [long]$SessionId = 910000000000000000,
    [string]$OutputRoot = "$PSScriptRoot\results\fault-consistency-$(Get-Date -Format yyyyMMdd-HHmmss)"
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $OutputRoot | Out-Null
$runId = "fault-consistency-$(Get-Date -Format yyyyMMdd-HHmmss)"
$base = 'http://127.0.0.1:18083'
$token = ([guid]::NewGuid()).ToString('N')
$payload = '{"@class":"com.anyujia.anyujiacommonsecurity.domain.dto.LoginUserDTO","userId":1,"userName":"Dev Tenant","userFrom":"app","userType":1}'
docker exec frameworkjava-redis-master redis-cli -a change-me --no-auth-warning SET "login:token:$token" $payload EX 7200 | Out-Null
$headers = @{ Authorization = "Bearer $token"; userId = '1'; userFrom = 'app'; userType = '1'; userName = 'Dev Tenant'; 'Content-Type' = 'application/json' }

function Db([string]$sql) { [string](docker exec frameworkjava-mysql mysql -uappdev -pchange-me frameworkjava_dev -Nse $sql) }
function Send([string]$id, [string]$content = 'fault consistency') {
    $body = @{ sessionId = $SessionId; clientMessageId = $id; type = 0; content = $content } | ConvertTo-Json -Compress
    for ($attempt = 1; $attempt -le 8; $attempt++) {
        try { return Invoke-RestMethod -Method Post -Uri "$base/chat/message/send" -Headers $headers -Body $body -TimeoutSec 20 } catch { if ($attempt -eq 8) { return $null }; Start-Sleep -Seconds 2 }
    }
}
function Wait-Port([int]$seconds = 60) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do { if (Test-NetConnection 127.0.0.1 -Port 18083 -InformationLevel Quiet) { return $true }; Start-Sleep -Milliseconds 500 } while ((Get-Date) -lt $deadline)
    $false
}
function Wait-Outbox([long]$messageId, [int]$seconds = 45) {
    $deadline = (Get-Date).AddSeconds($seconds); $count = 0
    do { $count = [int](Db "SELECT COUNT(*) FROM frameworkjava_dev.chat_message_outbox WHERE message_id=$messageId"); if ($count -ge 4) { return $count }; Start-Sleep -Seconds 3 } while ((Get-Date) -lt $deadline)
    $count
}
function Save-Result([string]$name, $value) { $value | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $OutputRoot "$name.json") -Encoding UTF8 }

$all = @()

# 1. Worker kill during fixed-client-id writes, followed by retries.
$ids = 1..$MessageCount | ForEach-Object { "$runId-worker-$_" }
$acceptedBeforeKill = 0
foreach ($id in $ids[0..([Math]::Min(9, $ids.Count - 1))]) { if ((Send $id 'worker pre-kill')) { $acceptedBeforeKill++ }; Start-Sleep -Milliseconds 50 }
$killAt = Get-Date
docker kill -s KILL frameworkjava-chat | Out-Null
Start-Sleep -Seconds 2
docker start frameworkjava-chat | Out-Null
$recovered = Wait-Port
Start-Sleep -Seconds 10
$acceptedAfterRetry = 0
foreach ($id in $ids) { if ((Send $id 'worker fixed-id retry')) { $acceptedAfterRetry++ }; Start-Sleep -Milliseconds 50 }
$prefix = "$runId-worker-%"
$dbCount = [int](Db "SELECT COUNT(*) FROM frameworkjava_dev.chat_message WHERE client_message_id LIKE '$prefix'")
$dupClient = [int](Db "SELECT COUNT(*) FROM (SELECT client_message_id FROM frameworkjava_dev.chat_message WHERE client_message_id LIKE '$prefix' GROUP BY client_message_id HAVING COUNT(*)>1) x")
$dupSeq = [int](Db "SELECT COUNT(*) FROM (SELECT conversation_id,conversation_seq FROM frameworkjava_dev.chat_message WHERE client_message_id LIKE '$prefix' GROUP BY conversation_id,conversation_seq HAVING COUNT(*)>1) x")
$seq = @(Db "SELECT conversation_seq FROM frameworkjava_dev.chat_message WHERE client_message_id LIKE '$prefix' ORDER BY conversation_seq" | Where-Object { $_ -match '^\d+$' } | ForEach-Object { [long]$_ })
$gaps = 0; for ($i = 1; $i -lt $seq.Count; $i++) { if ($seq[$i] -ne $seq[$i - 1] + 1) { $gaps++ } }
$missingOutbox = [int](Db "SELECT COUNT(*) FROM frameworkjava_dev.chat_message m LEFT JOIN chat_message_outbox o ON o.message_id=m.id WHERE m.client_message_id LIKE '$prefix' AND o.id IS NULL")
$workerResult = [ordered]@{ scenario = 'continuous-write-kill-worker-fixed-id-retry'; runId = $runId; target = $MessageCount; acceptedBeforeKill = $acceptedBeforeKill; retryAttempts = $MessageCount; acceptedAfterRetry = $acceptedAfterRetry; recovered = $recovered; dbPersisted = $dbCount; duplicateClientMessageIds = $dupClient; duplicateConversationSeq = $dupSeq; sequenceGaps = $gaps; missingOutbox = $missingOutbox; passed = ($recovered -and $dbCount -eq $MessageCount -and $dupClient -eq 0 -and $dupSeq -eq 0 -and $gaps -eq 0) }
Save-Result '01-worker-kill' $workerResult; $all += $workerResult

# 2. RabbitMQ stopped while writes continue, then recovery and backlog check.
$mqIds = 1..20 | ForEach-Object { "$runId-mq-$_" }
$beforeUnread = [int](Db "SELECT COALESCE(unread_count,0) FROM frameworkjava_dev.chat_user_conversation WHERE user_id=2 AND conversation_id=$SessionId")
docker stop frameworkjava-rabbitmq | Out-Null
$mqStoppedAt = Get-Date
$mqAccepted = 0
foreach ($id in $mqIds) { if ((Send $id 'rabbit stopped write')) { $mqAccepted++ }; Start-Sleep -Milliseconds 100 }
docker start frameworkjava-rabbitmq | Out-Null
Start-Sleep -Seconds 10
$mqHealth = 'starting'
$healthDeadline = (Get-Date).AddSeconds(45)
do { $mqHealth = docker inspect frameworkjava-rabbitmq --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}'; if ($mqHealth -eq 'healthy') { break }; Start-Sleep -Seconds 3 } while ((Get-Date) -lt $healthDeadline)
Start-Sleep -Seconds 20
$mqPrefix = "$runId-mq-%"
$mqDb = [int](Db "SELECT COUNT(*) FROM frameworkjava_dev.chat_message WHERE client_message_id LIKE '$mqPrefix'")
$mqPending = [int](Db "SELECT COUNT(*) FROM chat_message_outbox o JOIN chat_message m ON m.id=o.message_id WHERE m.client_message_id LIKE '$mqPrefix' AND o.status IN (0,1)")
$afterUnread = [int](Db "SELECT COALESCE(unread_count,0) FROM frameworkjava_dev.chat_user_conversation WHERE user_id=2 AND conversation_id=$SessionId")
$mqResult = [ordered]@{ scenario = 'rabbitmq-stop-during-continuous-write'; runId = $runId; stoppedAt = $mqStoppedAt.ToString('o'); acceptedWhileStopped = $mqAccepted; dbPersisted = $mqDb; rabbitHealthAfterRecovery = $mqHealth; outboxPendingAfterRecovery = $mqPending; unreadBefore = $beforeUnread; unreadAfter = $afterUnread; unreadDelta = ($afterUnread - $beforeUnread); passed = ($mqDb -eq 20 -and $mqPending -eq 0 -and $mqHealth -eq 'healthy') }
Save-Result '02-rabbit-stop' $mqResult; $all += $mqResult

# 3. Late visibility / cursor-lag reproduction and overlap-window repair.
$lateId = "$runId-late"
$late = Send $lateId 'late commit cursor test'
$lateMessageId = [long]$late.data.messageId
Start-Sleep -Seconds 5
$cursorBefore = [long](Db "SELECT last_message_id FROM chat_outbox_reconcile_cursor WHERE source_key='legacy'")
docker exec frameworkjava-mysql mysql -uappdev -pchange-me frameworkjava_dev -Nse "UPDATE chat_outbox_reconcile_cursor SET last_message_id=$lateMessageId WHERE source_key='legacy';" | Out-Null
docker exec frameworkjava-mysql mysql -uappdev -pchange-me -Nse "DELETE FROM frameworkjava_dev.chat_message_outbox WHERE message_id=$lateMessageId;" | Out-Null
$cursorMissCount = [int](Wait-Outbox $lateMessageId 20)
docker exec frameworkjava-mysql mysql -uappdev -pchange-me frameworkjava_dev -Nse "UPDATE chat_outbox_reconcile_cursor SET last_message_id=$($lateMessageId-1) WHERE source_key='legacy';" | Out-Null
$overlapRepairCount = [int](Wait-Outbox $lateMessageId 30)
$lateResult = [ordered]@{ scenario = 'late-commit-cursor-overlap-repair'; runId = $runId; messageId = $lateMessageId; cursorBefore = $cursorBefore; countAfterCursorMissWindow = $cursorMissCount; countAfterOverlapRepair = $overlapRepairCount; passed = ($cursorMissCount -eq 0 -and $overlapRepairCount -ge 4) }
Save-Result '03-late-commit' $lateResult; $all += $lateResult

# 4. Re-run compensation twice; unique key must keep exactly four effects.
docker exec frameworkjava-mysql mysql -uappdev -pchange-me frameworkjava_dev -Nse "DELETE FROM chat_message_outbox WHERE message_id=$lateMessageId;" | Out-Null
docker exec frameworkjava-mysql mysql -uappdev -pchange-me frameworkjava_dev -Nse "UPDATE chat_outbox_reconcile_cursor SET last_message_id=$($lateMessageId-1) WHERE source_key='legacy';" | Out-Null
$firstCompensation = [int](Wait-Outbox $lateMessageId 30)
docker exec frameworkjava-mysql mysql -uappdev -pchange-me frameworkjava_dev -Nse "UPDATE chat_outbox_reconcile_cursor SET last_message_id=$($lateMessageId-1) WHERE source_key='legacy';" | Out-Null
Start-Sleep -Seconds 15
$secondCompensation = [int](Db "SELECT COUNT(*) FROM frameworkjava_dev.chat_message_outbox WHERE message_id=$lateMessageId")
$duplicateEffects = [int](Db "SELECT COUNT(*) - COUNT(DISTINCT event_type,recipient_id) FROM frameworkjava_dev.chat_message_outbox WHERE message_id=$lateMessageId")
$idempotentResult = [ordered]@{ scenario = 'repeated-compensation-idempotency'; runId = $runId; messageId = $lateMessageId; firstCompensationRows = $firstCompensation; secondCompensationRows = $secondCompensation; duplicateEffects = $duplicateEffects; passed = ($firstCompensation -eq 4 -and $secondCompensation -eq 4 -and $duplicateEffects -eq 0) }
Save-Result '04-compensation-idempotency' $idempotentResult; $all += $idempotentResult

$all | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $OutputRoot 'summary.json') -Encoding UTF8
$all | ConvertTo-Json -Depth 10
