param(
    [long]$SessionId = 910000000000000031,
    [string]$OutputRoot = "$PSScriptRoot\results\transaction-ab-$(Get-Date -Format yyyyMMdd-HHmmss)"
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $OutputRoot | Out-Null
$runId = "transaction-ab-$(Get-Date -Format yyyyMMdd-HHmmss)"
function Db([string]$sql) { [string](docker exec frameworkjava-mysql mysql -uappdev -pchange-me frameworkjava_dev -Nse $sql) }
 $physicalShard = [int]([math]::Abs($SessionId % 1024) % 32)
 $shardContainer = "frameworkjava-mysql-shard-$([int][math]::Floor($physicalShard / 8))"
 $shardTable = "chat_message_s{0:d2}" -f $physicalShard
 $source = "shard-$physicalShard"
function Shard-Db([string]$sql) { [string](docker exec $shardContainer mysql -uappdev -pchange-me frameworkjava_dev -Nse $sql) }
function Save($name, $value) { $value | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $OutputRoot $name) -Encoding UTF8 }

$max = Shard-Db "SELECT COALESCE(MAX(id),0),COALESCE(MAX(conversation_seq),0) FROM $shardTable WHERE conversation_id=$SessionId"
$parts = $max -split "`t"
$baseId = [long]$parts[0]
$baseSeq = [long]$parts[1]
$lateId = $baseId + 1
$fastId = $baseId + 2
$lateSeq = $baseSeq + 1
$fastSeq = $baseSeq + 2
$lateClient = "$runId-late"
$fastClient = "$runId-fast"

$lateSql = "START TRANSACTION; INSERT INTO $shardTable (id,client_message_id,conversation_id,conversation_seq,sender_id,recipient_id,type,content,status,visited,created_at) VALUES ($lateId,'$lateClient',$SessionId,$lateSeq,1,2,0,'transaction A late commit',0,10,CURRENT_TIMESTAMP(3)); SELECT SLEEP(20); COMMIT;"
$job = Start-Job -ScriptBlock { param($container, $sql) docker exec $container mysql -uappdev -pchange-me frameworkjava_dev -Nse $sql } -ArgumentList $shardContainer, $lateSql
Start-Sleep -Seconds 2

# Transaction B commits a larger ID first. This is the row that can move a monotonic cursor
# beyond the still-uncommitted smaller ID from transaction A.
$fastSql = "START TRANSACTION; INSERT INTO $shardTable (id,client_message_id,conversation_id,conversation_seq,sender_id,recipient_id,type,content,status,visited,created_at) VALUES ($fastId,'$fastClient',$SessionId,$fastSeq,1,2,0,'transaction B early commit',0,10,CURRENT_TIMESTAMP(3)); COMMIT;"
docker exec $shardContainer mysql -uappdev -pchange-me frameworkjava_dev -Nse $fastSql | Out-Null
docker exec frameworkjava-mysql mysql -uappdev -pchange-me frameworkjava_dev -Nse "UPDATE chat_outbox_reconcile_cursor SET last_message_id=$fastId WHERE source_key='$source';" | Out-Null

$bCommittedAt = Get-Date
Wait-Job $job -Timeout 35 | Out-Null
$aOutput = Receive-Job $job -ErrorAction SilentlyContinue
Remove-Job $job -Force -ErrorAction SilentlyContinue
$aCommittedAt = Get-Date
Start-Sleep -Seconds 15

$lateOutbox = [int](Db "SELECT COUNT(*) FROM chat_message_outbox WHERE message_id=$lateId")
$lateDuplicate = [int](Db "SELECT COUNT(*)-COUNT(DISTINCT event_type,recipient_id) FROM chat_message_outbox WHERE message_id=$lateId")
$fastOutbox = [int](Db "SELECT COUNT(*) FROM chat_message_outbox WHERE message_id=$fastId")
$cursor = [long](Db "SELECT last_message_id FROM chat_outbox_reconcile_cursor WHERE source_key='$source'")
$result = [ordered]@{
    runId = $runId
    sessionId = $SessionId
    physicalShard = $physicalShard
    sourceKey = $source
    transactionA = [ordered]@{ messageId = $lateId; conversationSeq = $lateSeq; committedAt = $aCommittedAt.ToString('o') }
    transactionB = [ordered]@{ messageId = $fastId; conversationSeq = $fastSeq; committedAt = $bCommittedAt.ToString('o') }
    cursorAfterB = $fastId
    cursorAfterTest = $cursor
    lateMessageOutboxRows = $lateOutbox
    fastMessageOutboxRows = $fastOutbox
    lateDuplicateEffects = $lateDuplicate
    overlapRepairPassed = ($lateOutbox -eq 4 -and $lateDuplicate -eq 0)
}
Save 'transaction-ab-result.json' $result
($result | ConvertTo-Json -Depth 10)
