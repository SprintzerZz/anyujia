// Node 20+: measure A upload -> send -> B sync or WebSocket push -> image download.
// Run against an isolated development environment. Each sample creates one asset and message.
import { randomBytes, randomUUID } from 'node:crypto';
import { writeFile } from 'node:fs/promises';
import { deflateSync } from 'node:zlib';
import { performance } from 'node:perf_hooks';

const baseUrl = (process.env.CHAT_BASE_URL || 'http://127.0.0.1:18083').replace(/\/$/, '');
const sessionId = process.env.CHAT_SESSION_ID || '900000000000100001';
const senderId = process.env.CHAT_SENDER_ID || '1';
const recipientId = process.env.CHAT_RECIPIENT_ID || '2';
const levels = (process.env.CHAT_CONCURRENCY || '1,2,4,8').split(',').map(Number);
const samplesPerLevel = Number(process.env.CHAT_SAMPLES_PER_LEVEL || 20);
const output = process.env.CHAT_RESULT_FILE || `chat-image-e2e-${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
const timeoutMs = Number(process.env.CHAT_TIMEOUT_MS || 15000);
const receiveMode = process.env.CHAT_RECEIVE_MODE || 'sync';
const uploadRetries = Number(process.env.CHAT_UPLOAD_RETRIES || 1);
const headers = (userId) => ({ userId, userName: `perf-user-${userId}`, userFrom: 'app' });
const pendingPush = new Map();
let recipientSocket;

function crc32(buffer) {
  let crc = -1;
  for (const byte of buffer) {
    crc ^= byte;
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
  }
  return (crc ^ -1) >>> 0;
}

function chunk(type, data) {
  const name = Buffer.from(type);
  const size = Buffer.alloc(4);
  size.writeUInt32BE(data.length);
  const sum = Buffer.alloc(4);
  sum.writeUInt32BE(crc32(Buffer.concat([name, data])));
  return Buffer.concat([size, name, data, sum]);
}

function testPng() {
  const width = 256;
  const height = 256;
  const pixels = Buffer.alloc(height * (1 + width * 3));
  for (let y = 0; y < height; y++) randomBytes(width * 3).copy(pixels, y * (1 + width * 3) + 1);
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8; // 8-bit RGB
  header[9] = 2;
  return Buffer.concat([
    Buffer.from('89504e470d0a1a0a', 'hex'),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(pixels)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

async function api(path, userId, body, multipart = false) {
  const response = await fetch(`${baseUrl}${path}`, {
    method: 'POST',
    headers: multipart ? headers(userId) : { ...headers(userId), 'content-type': 'application/json' },
    body: multipart ? body : JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs),
  });
  const payload = await response.json();
  if (!response.ok || payload.code !== 200000) {
    throw new Error(`${path}: HTTP ${response.status}, code=${payload.code}, message=${payload.message || payload.msg || ''}`);
  }
  return payload.data;
}

async function connectRecipient() {
  const ticket = await api('/chat/websocket/ticket', recipientId);
  const wsBase = (process.env.CHAT_WS_URL || 'ws://127.0.0.1:18085').replace(/\/$/, '');
  const socket = new WebSocket(`${wsBase}/chat/websocket?ticket=${encodeURIComponent(ticket.ticket)}`);
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('B WebSocket connect timeout')), timeoutMs);
    socket.addEventListener('open', () => { clearTimeout(timer); resolve(); }, { once: true });
    socket.addEventListener('error', () => { clearTimeout(timer); reject(new Error('B WebSocket connect failed')); }, { once: true });
  });
  socket.addEventListener('message', (event) => {
    if (process.env.CHAT_DEBUG_FRAMES === '1') console.error('B frame:', String(event.data).slice(0, 500));
    let frame;
    try { frame = JSON.parse(String(event.data)); } catch { return; }
    if (frame.type !== 'chat' || !frame.data?.clientMessageId) return;
    const waiter = pendingPush.get(frame.data.clientMessageId);
    if (waiter) waiter.resolve({ message: frame.data, at: performance.now() });
  });
  socket.addEventListener('close', () => {
    for (const waiter of pendingPush.values()) waiter.reject(new Error('B WebSocket closed'));
  });
  recipientSocket = socket;
  // WebSocket OPEN precedes presence publication on the server.
  await new Promise((resolve) => setTimeout(resolve, 500));
}

function waitForPush(clientMessageId) {
  let timer;
  const promise = new Promise((resolve, reject) => {
    timer = setTimeout(() => reject(new Error(`B push timeout for ${clientMessageId}`)), timeoutMs);
    pendingPush.set(clientMessageId, { resolve, reject });
  }).finally(() => {
    clearTimeout(timer);
    pendingPush.delete(clientMessageId);
  });
  return promise;
}

async function one(png, level, index) {
  const started = performance.now();
  const uploadId = randomUUID();
  const clientMessageId = `imgperf-${randomUUID()}`;
  try {
    const form = new FormData();
    form.set('file', new Blob([png], { type: 'image/png' }), 'perf.png');
    let image;
    let uploadAttempts = 0;
    for (; uploadAttempts < uploadRetries; uploadAttempts++) {
      try {
        image = await api(`/chat/image/upload?sessionId=${sessionId}&uploadId=${uploadId}`, senderId, form, true);
        uploadAttempts++;
        break;
      } catch (error) {
        if (uploadAttempts + 1 >= uploadRetries || !String(error).includes('Too many image uploads')) throw error;
        await new Promise((resolve) => setTimeout(resolve, 250 * 2 ** uploadAttempts));
      }
    }
    const uploaded = performance.now();
    const pushed = receiveMode === 'push' ? waitForPush(clientMessageId) : null;
    pushed?.catch(() => {}); // A failed send may leave a push waiter that times out later.
    const sendStarted = performance.now();
    const sent = await api('/chat/message/send', senderId, {
      sessionId, clientMessageId, type: 1, content: image.assetId,
    });
    const sentAt = performance.now();
    let received;
    let visibleAt;
    if (receiveMode === 'push') {
      ({ message: received, at: visibleAt } = await pushed);
    } else {
      const afterConversationSeq = String(BigInt(sent.conversationSeq) - 1n);
      const messages = await api('/chat/message/sync', recipientId,
        { sessionId, afterConversationSeq, count: 100 });
      received = messages.find((message) => message.messageId === sent.messageId);
      visibleAt = performance.now();
      if (!received) throw new Error('B sync did not return the sent message');
    }
    if (received.messageId !== sent.messageId) throw new Error('B received a different message ID');
    if (received.type !== 1 || received.image?.assetId !== image.assetId) {
      throw new Error('B received different image metadata');
    }
    const imageResponse = await fetch(received.image.url, { signal: AbortSignal.timeout(timeoutMs) });
    const downloaded = Buffer.from(await imageResponse.arrayBuffer());
    if (!imageResponse.ok || downloaded.length !== png.length) {
      throw new Error(`B image download failed: HTTP ${imageResponse.status}, bytes=${downloaded.length}`);
    }
    const finished = performance.now();
    return {
      level, index, ok: true, assetId: image.assetId, messageId: sent.messageId, uploadAttempts,
      uploadMs: uploaded - started, sendMs: sentAt - uploaded,
      bReceiveAfterSendStartMs: visibleAt - sendStarted,
      bReceiveAfterSendResponseMs: visibleAt - sentAt,
      downloadMs: finished - visibleAt, totalMs: finished - started,
    };
  } catch (error) {
    return { level, index, ok: false, error: String(error), elapsedMs: performance.now() - started };
  }
}

function stats(values) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const at = (p) => Math.round(sorted[Math.max(0, Math.min(sorted.length - 1, Math.ceil(p * sorted.length) - 1))] * 10) / 10;
  return { min: at(0), p50: at(0.5), p95: at(0.95), p99: at(0.99), max: at(1) };
}

async function main() {
  if (levels.some((n) => !Number.isInteger(n) || n < 1) || !Number.isInteger(samplesPerLevel)
      || samplesPerLevel < 1 || !Number.isInteger(uploadRetries) || uploadRetries < 1) {
    throw new Error('Concurrency and sample count must be positive integers');
  }
  const health = await fetch(`${baseUrl}/actuator/health`, { signal: AbortSignal.timeout(3000) });
  if (!health.ok) throw new Error(`Chat health HTTP ${health.status}`);
  if (!['sync', 'push'].includes(receiveMode)) throw new Error('CHAT_RECEIVE_MODE must be sync or push');
  if (receiveMode === 'push') await connectRecipient();
  const png = testPng();
  const warmupSamples = receiveMode === 'sync' ? 2 : 0;
  for (let i = 0; i < warmupSamples; i++) {
    const warmup = await one(png, 0, i);
    if (!warmup.ok) throw new Error(`Warmup failed: ${warmup.error}`);
  }
  const results = [];
  const summaries = [];
  for (const level of levels) {
    const started = performance.now();
    for (let offset = 0; offset < samplesPerLevel; offset += level) {
      const batch = Array.from({ length: Math.min(level, samplesPerLevel - offset) }, (_, i) => one(png, level, offset + i));
      results.push(...await Promise.all(batch));
    }
    const durationSeconds = (performance.now() - started) / 1000;
    const group = results.filter((entry) => entry.level === level);
    const success = group.filter((entry) => entry.ok);
    const summary = {
      concurrency: level, attempted: group.length, succeeded: success.length,
      failed: group.length - success.length, durationSeconds: Math.round(durationSeconds * 100) / 100,
      successfulPerSecond: Math.round(success.length / durationSeconds * 100) / 100,
      retriedUploads: success.filter((entry) => entry.uploadAttempts > 1).length,
      uploadMs: stats(success.map((entry) => entry.uploadMs)),
      sendMs: stats(success.map((entry) => entry.sendMs)),
      bReceiveAfterSendStartMs: stats(success.map((entry) => entry.bReceiveAfterSendStartMs)),
      bReceiveAfterSendResponseMs: stats(success.map((entry) => entry.bReceiveAfterSendResponseMs)),
      downloadMs: stats(success.map((entry) => entry.downloadMs)),
      totalMs: stats(success.map((entry) => entry.totalMs)),
      failures: [...new Set(group.filter((entry) => !entry.ok).map((entry) => entry.error))],
    };
    summaries.push(summary);
    console.log(JSON.stringify(summary));
  }
  await writeFile(output, JSON.stringify({
    generatedAt: new Date().toISOString(), baseUrl, sessionId, senderId, recipientId, receiveMode,
    imageBytes: png.length, warmupSamples, timeoutMs, uploadRetries, samplesPerLevel, summaries, results,
  }, null, 2));
  recipientSocket?.close();
  console.log(`Wrote ${output}`);
}

main().catch((error) => { recipientSocket?.close(); console.error(error); process.exitCode = 1; });
