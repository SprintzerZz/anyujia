// Node 20+. Development-only: 100 distinct tenants consult the same landlord/listing.
// Uses direct chat-service test headers, so do not point this at a public gateway.
import { randomBytes, randomUUID } from 'node:crypto';
import { writeFile } from 'node:fs/promises';
import { performance } from 'node:perf_hooks';
import { deflateSync } from 'node:zlib';

const baseUrl = (process.env.CHAT_BASE_URL || 'http://127.0.0.1:18083').replace(/\/$/, '');
const wsUrl = (process.env.CHAT_WS_URL || 'ws://127.0.0.1:18085').replace(/\/$/, '');
const landlordId = process.env.CHAT_LANDLORD_ID || '2';
const listingId = process.env.CHAT_LISTING_ID || '900000000000000001';
const count = Number(process.env.CHAT_TENANTS || 100);
const concurrency = Number(process.env.CHAT_TENANT_CONCURRENCY || count);
const imageConcurrency = Number(process.env.CHAT_IMAGE_CONCURRENCY || 8);
const sendImages = process.env.CHAT_SEND_IMAGES !== 'false';
const uploadRetries = Number(process.env.CHAT_UPLOAD_RETRIES || 7);
const timeoutMs = Number(process.env.CHAT_TIMEOUT_MS || 15000);
const settleMs = Number(process.env.CHAT_SETTLE_MS || 45000);
const output = process.env.CHAT_RESULT_FILE ||
  `deploy/perf/results/chat-hot-listing-${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
const tenantBase = 940000000000000000n;
const conversationBase = 941000000000000000n;
const headers = (id) => ({ userId: id, userName: `perf-${id}`, userFrom: 'app' });
const round = (n) => Math.round(n * 10) / 10;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
let socket;
const allSessions = Array.from({ length: count }, (_, i) => ({
  tenantId: String(tenantBase + BigInt(i + 1)),
  sessionId: String(conversationBase + BigInt(i + 1)),
}));

function percentile(values) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const at = (p) => round(sorted[Math.max(0, Math.ceil(p * sorted.length) - 1)]);
  return { min: at(0), p50: at(0.5), p95: at(0.95), p99: at(0.99), max: at(1) };
}

function crc32(buffer) {
  let crc = -1;
  for (const byte of buffer) {
    crc ^= byte;
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
  }
  return (crc ^ -1) >>> 0;
}

function pngChunk(type, data) {
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
  header[8] = 8;
  header[9] = 2;
  return Buffer.concat([
    Buffer.from('89504e470d0a1a0a', 'hex'),
    pngChunk('IHDR', header),
    pngChunk('IDAT', deflateSync(pixels)),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

async function api(path, userId, body, multipart = false) {
  const response = await fetch(`${baseUrl}${path}`, {
    method: 'POST',
    headers: multipart ? headers(userId) : { ...headers(userId), 'content-type': 'application/json' },
    body: multipart ? body : JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs),
  });
  let payload;
  try { payload = await response.json(); } catch { throw new Error(`${path}: HTTP ${response.status}, invalid JSON`); }
  if (!response.ok || payload.code !== 200000) {
    throw new Error(`${path}: HTTP ${response.status}, code=${payload.code}, message=${payload.message || payload.msg || ''}`);
  }
  return payload.data;
}

async function inbox(fresh = false) {
  const items = [];
  const seen = new Set();
  // A synthetic upper cursor bypasses the first-page Redis cache for the
  // baseline. The post-load check intentionally uses the normal cached path.
  let cursor = fresh ? { lastSessionTime: Number.MAX_SAFE_INTEGER,
    sessionId: '9223372036854775807' } : undefined;
  for (let page = 0; page < 100; page++) {
    const data = await api('/chat/session/list', landlordId,
      cursor ? { count: 100, lastSessionTime: cursor.lastSessionTime, lastSessionId: cursor.sessionId }
        : { count: 100 });
    if (!Array.isArray(data)) throw new Error('Session list is not an array');
    for (const item of data) {
      if (seen.has(item.sessionId)) throw new Error(`Session list repeated ${item.sessionId}`);
      seen.add(item.sessionId);
      items.push(item);
    }
    if (data.length < 100) return items;
    cursor = data.at(-1);
  }
  throw new Error('Session list exceeded 100 pages');
}

function checkOrder(items) {
  for (let i = 1; i < items.length; i++) {
    const previous = items[i - 1];
    const current = items[i];
    if (previous.lastSessionTime < current.lastSessionTime ||
        (previous.lastSessionTime === current.lastSessionTime &&
          BigInt(previous.sessionId) < BigInt(current.sessionId))) {
      return { ok: false, index: i, previous: previous.sessionId, current: current.sessionId };
    }
  }
  return { ok: true, checked: items.length };
}

async function pool(items, limit, worker) {
  let next = 0;
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (next < items.length) {
      const index = next++;
      await worker(items[index], index);
    }
  }));
}

async function uploadImage(session, png) {
  const uploadId = randomUUID();
  let lastError;
  for (let attempt = 1; attempt <= uploadRetries; attempt++) {
    const form = new FormData();
    form.set('file', new Blob([png], { type: 'image/png' }), 'perf.png');
    try {
      const image = await api(`/chat/image/upload?sessionId=${session.sessionId}&uploadId=${uploadId}`,
        session.tenantId, form, true);
      return { image, attempts: attempt };
    } catch (error) {
      lastError = error;
      if (attempt === uploadRetries || !String(error).includes('Too many image uploads')) break;
      await sleep(Math.min(2000, 200 * 2 ** (attempt - 1)) + Math.floor(Math.random() * 100));
    }
  }
  throw lastError;
}

async function main() {
  if (![count, concurrency, imageConcurrency, uploadRetries, timeoutMs, settleMs]
      .every((n) => Number.isInteger(n) && n > 0)
      || count > 100 || !/^\d+$/.test(landlordId) || !/^\d+$/.test(listingId)) {
    throw new Error('CHAT_TENANTS must be 1..100; concurrency, retries and timeouts must be positive integers');
  }
  const health = await fetch(`${baseUrl}/actuator/health`, { signal: AbortSignal.timeout(3000) });
  if (!health.ok) throw new Error(`Chat health HTTP ${health.status}`);
  await pool(allSessions, 10, async (session) => {
    const belongs = await api('/chat/session/has_house', session.tenantId,
      { sessionId: session.sessionId, housePublishId: listingId });
    if (belongs !== true) throw new Error(`Fixture missing for tenant ${session.tenantId}`);
  });
  const before = await inbox(true);
  const beforeBySession = new Map(before.map((item) => [item.sessionId, item]));
  const baselineOrder = checkOrder(before);
  if (!baselineOrder.ok) throw new Error(`Inbox was unsorted before load: ${JSON.stringify(baselineOrder)}`);

  const ticket = await api('/chat/websocket/ticket', landlordId);
  socket = new WebSocket(`${wsUrl}/chat/websocket?ticket=${encodeURIComponent(ticket.ticket)}`);
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Landlord WebSocket connect timeout')), timeoutMs);
    socket.addEventListener('open', () => { clearTimeout(timer); resolve(); }, { once: true });
    socket.addEventListener('error', () => { clearTimeout(timer); reject(new Error('Landlord WebSocket connect failed')); }, { once: true });
  });
  const pushes = new Map();
  const pushCounts = new Map();
  socket.addEventListener('message', (event) => {
    let frame;
    try { frame = JSON.parse(String(event.data)); } catch { return; }
    if (frame.type === 'chat' && frame.data?.clientMessageId) {
      const clientId = frame.data.clientMessageId;
      pushCounts.set(clientId, (pushCounts.get(clientId) || 0) + 1);
      if (!pushes.has(clientId)) {
        pushes.set(clientId, { at: performance.now(), message: frame.data });
      }
      if (frame.data.messageId && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ version: 1, requestId: randomUUID(), type: 'delivery_ack',
          data: { messageId: frame.data.messageId } }));
      }
    }
  });
  // Server presence registration can finish just after the WebSocket opens.
  await sleep(500);

  const png = testPng();
  const runId = randomUUID();
  const samples = [];
  const started = performance.now();
  await pool(allSessions, concurrency, async (session, index) => {
    const text = {
      kind: 'text', tenantId: session.tenantId, sessionId: session.sessionId,
      clientMessageId: `hot-${runId}-${index}-text`,
    };
    text.sendStartedAt = performance.now();
    try {
      const sent = await api('/chat/message/send', session.tenantId, {
        sessionId: session.sessionId, clientMessageId: text.clientMessageId,
        type: 0, content: `Hot listing inquiry ${runId} tenant ${index + 1}`,
      });
      text.messageId = sent.messageId;
      text.sendMs = round(performance.now() - text.sendStartedAt);
      text.ok = true;
    } catch (error) { text.ok = false; text.error = String(error); }
    samples.push(text);
  });
  if (sendImages) await pool(allSessions, imageConcurrency, async (session, index) => {
    const image = {
      kind: 'image', tenantId: session.tenantId, sessionId: session.sessionId,
      clientMessageId: `hot-${runId}-${index}-image`,
    };
    const uploadStarted = performance.now();
    try {
      const uploaded = await uploadImage(session, png);
      image.uploadOk = true;
      image.uploadAttempts = uploaded.attempts;
      image.uploadMs = round(performance.now() - uploadStarted);
      image.assetId = uploaded.image.assetId;
      image.sendStartedAt = performance.now();
      const sent = await api('/chat/message/send', session.tenantId, {
        sessionId: session.sessionId, clientMessageId: image.clientMessageId,
        type: 1, content: image.assetId,
      });
      image.messageId = sent.messageId;
      image.sendMs = round(performance.now() - image.sendStartedAt);
      image.ok = true;
    } catch (error) {
      image.uploadOk ??= false;
      image.ok = false;
      image.error = String(error);
    }
    samples.push(image);
  });
  const sendsFinishedAt = performance.now();
  const successful = samples.filter((sample) => sample.ok);
  const deadline = performance.now() + settleMs;
  while (performance.now() < deadline && successful.some((sample) => !pushes.has(sample.clientMessageId))) {
    await sleep(100);
  }
  for (const sample of successful) {
    const push = pushes.get(sample.clientMessageId);
    if (!push) continue;
    sample.pushed = push.message.messageId === sample.messageId &&
      String(push.message.sessionId) === sample.sessionId &&
      (sample.kind !== 'image' || push.message.image?.assetId === sample.assetId);
    if (sample.pushed) {
      sample.pushFromSendStartMs = round(push.at - sample.sendStartedAt);
      sample.pushAfterResponseMs = round(push.at - (sample.sendStartedAt + sample.sendMs));
    }
  }

  const expected = new Map(allSessions.map((session) => {
    const sent = successful.filter((sample) => sample.sessionId === session.sessionId);
    const previous = beforeBySession.get(session.sessionId);
    return [session.sessionId, {
      unread: Number(previous?.notVisitedCount || 0) + sent.length,
      messageId: sent.at(-1)?.messageId || previous?.lastMessageId,
      shouldAppear: sent.length > 0 || !!previous,
    }];
  }));
  let after;
  let inboxProblems;
  do {
    after = await inbox();
    const bySession = new Map(after.map((item) => [item.sessionId, item]));
    inboxProblems = [];
    for (const session of allSessions) {
      const want = expected.get(session.sessionId);
      const got = bySession.get(session.sessionId);
      if (want.shouldAppear && (!got || Number(got.notVisitedCount) !== want.unread ||
          got.lastMessageId !== want.messageId)) {
        inboxProblems.push({ sessionId: session.sessionId, expected: want,
          actual: got && { unread: got.notVisitedCount, messageId: got.lastMessageId } });
      }
    }
    if (inboxProblems.length) await sleep(250);
  } while (inboxProblems.length && performance.now() < deadline);
  const afterOrder = checkOrder(after);
  const summary = {
    tenants: count, concurrency, imageConcurrency, sendImages, listingId, landlordId, imageBytes: png.length,
    sends: { attempted: samples.length, succeeded: successful.length, failed: samples.length - successful.length,
      durationSeconds: round((sendsFinishedAt - started) / 1000),
      successfulPerSecond: round(successful.length / ((sendsFinishedAt - started) / 1000)) },
    text: { succeeded: samples.filter((s) => s.kind === 'text' && s.ok).length },
    images: { uploadsSucceeded: samples.filter((s) => s.kind === 'image' && s.uploadOk).length,
      messagesSucceeded: samples.filter((s) => s.kind === 'image' && s.ok).length,
      retried: samples.filter((s) => s.kind === 'image' && s.uploadAttempts > 1).length,
      uploadMs: percentile(samples.filter((s) => s.kind === 'image' && s.uploadOk).map((s) => s.uploadMs)) },
    sendMs: percentile(successful.map((s) => s.sendMs)),
    push: { received: successful.filter((s) => s.pushed).length,
      duplicateFramesObserved: successful.reduce((sum, sample) =>
        sum + Math.max(0, (pushCounts.get(sample.clientMessageId) || 0) - 1), 0),
      latencyFromSendStartMs: percentile(successful.filter((s) => s.pushed).map((s) => s.pushFromSendStartMs)),
      latencyAfterResponseMs: percentile(successful.filter((s) => s.pushed).map((s) => s.pushAfterResponseMs)),
      textFromSendStartMs: percentile(successful.filter((s) => s.pushed && s.kind === 'text')
        .map((s) => s.pushFromSendStartMs)),
      imageFromSendStartMs: percentile(successful.filter((s) => s.pushed && s.kind === 'image')
        .map((s) => s.pushFromSendStartMs)) },
    inbox: { expectedConversations: count, visibleConversations: after.filter((item) => expected.has(item.sessionId)).length,
      unreadAndLatestMatched: count - inboxProblems.length, order: afterOrder,
      settledAfterSendMs: round(performance.now() - sendsFinishedAt) },
    failures: [...new Set(samples.filter((s) => !s.ok).map((s) => s.error))],
    inboxProblems: inboxProblems.slice(0, 10),
  };
  const passed = summary.sends.failed === 0 && summary.push.received === successful.length &&
    summary.inbox.unreadAndLatestMatched === count && afterOrder.ok;
  await writeFile(output, JSON.stringify({ generatedAt: new Date().toISOString(), runId, baseUrl, wsUrl,
    timeoutMs, settleMs, uploadRetries, passed, summary, samples }, null, 2));
  socket.close();
  console.log(JSON.stringify({ passed, summary, output }, null, 2));
  if (!passed) process.exitCode = 1;
}

main().catch((error) => { socket?.close(); console.error(error); process.exitCode = 1; });
