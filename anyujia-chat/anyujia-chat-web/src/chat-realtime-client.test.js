import assert from 'node:assert/strict';
import test from 'node:test';
import {
  ChatRealtimeClient,
  ChatRequestError,
} from '../../../deploy/client/chat-realtime-client.js';

globalThis.WebSocket = { CONNECTING: 0, OPEN: 1 };

function createClient(options = {}) {
  return new ChatRealtimeClient({
    baseUrl: 'http://localhost',
    sessionId: '1',
    tokenProvider: async () => 'token',
    ...options,
  });
}

test('restores persisted outbox before connecting', async () => {
  const requests = [{
    sessionId: '1',
    clientMessageId: 'pending-1',
    type: 0,
    content: 'hello',
  }];
  const client = createClient({ outboxStore: { load: async () => requests } });
  let connected = false;
  client.connect = async () => { connected = true; };

  client.start('7');
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(connected, true);
  assert.deepEqual(client.outbox.get('pending-1'), requests[0]);
  assert.equal(client.lastConversationSeq, '7');
  client.stop();
});

test('persists outgoing messages and deletes them after acknowledgement', async () => {
  const writes = [];
  const deletes = [];
  const client = createClient({
    outboxStore: {
      load: async () => [],
      put: async (request) => writes.push(request.clientMessageId),
      delete: async (clientMessageId) => deletes.push(clientMessageId),
    },
  });

  client.send('hello', 0, 'message-1');
  client.socket = {};
  client.onFrame(client.socket, JSON.stringify({
    type: 'chat_ack',
    data: { clientMessageId: 'message-1', conversationSeq: '1' },
  }));
  await client.outboxWrite;

  assert.deepEqual(writes, ['message-1']);
  assert.deepEqual(deletes, ['message-1']);
  assert.equal(client.outbox.has('message-1'), false);
});

test('resends the persisted outbox after reconnect without changing clientMessageId', async () => {
  const client = createClient();
  const request = {
    sessionId: '1',
    clientMessageId: 'retry-1',
    type: 0,
    content: 'retry me',
  };
  const sent = [];
  client.outbox.set(request.clientMessageId, request);
  client.socket = {
    readyState: WebSocket.OPEN,
    send(payload) { sent.push(JSON.parse(payload)); },
    close() {},
  };
  client.syncMissedMessages = async () => {};

  await client.onOpen(client.socket);

  const resent = sent.find((frame) => frame.type === 'chat');
  assert.ok(resent);
  assert.equal(resent.data.clientMessageId, 'retry-1');
  assert.equal(resent.version, 1);
  assert.equal(typeof resent.requestId, 'string');
  client.stop();
});

test('retries transient request failures but stops on authentication failures', () => {
  const client = createClient();

  assert.equal(client.shouldRetryRequest(new TypeError('network failed')), true);
  assert.equal(client.shouldRetryRequest(new ChatRequestError('busy', 503, 500000)), true);
  assert.equal(client.shouldRetryRequest(new ChatRequestError('expired', 401, 401003)), false);
  assert.equal(client.shouldRetryRequest(new ChatRequestError('invalid', 200, 400000)), false);
  assert.equal(client.shouldRetryRequest(new ChatRequestError('server', 200, 500000)), true);
});

test('classifies terminal and retryable WebSocket close codes', () => {
  const client = createClient();

  assert.equal(client.shouldRetryClose(1000), false);
  assert.equal(client.shouldRetryClose(4003), false);
  assert.equal(client.shouldRetryClose(4004), false);
  assert.equal(client.shouldRetryClose(1006), true);
  assert.equal(client.shouldRetryClose(1012), true);
  assert.equal(client.shouldRetryClose(4000), true);
});

test('closes a half-open socket when pong does not arrive', async () => {
  const client = createClient({ heartbeatIntervalMs: 1000, pongTimeoutMs: 5 });
  const socket = {
    readyState: 1,
    closeCode: null,
    sent: [],
    send(payload) { this.sent.push(JSON.parse(payload)); },
    close(code, reason) { this.closeCode = { code, reason }; },
  };
  client.socket = socket;
  client.startHeartbeat(socket);

  await new Promise((resolve) => setTimeout(resolve, 15));

  assert.equal(socket.sent[0].type, 'heart_beat');
  assert.equal(socket.sent[0].data, null);
  assert.equal(socket.sent[0].version, 1);
  assert.equal(typeof socket.sent[0].requestId, 'string');
  assert.deepEqual(socket.closeCode, { code: 4000, reason: 'Heartbeat timeout' });
  client.stop();
});

test('uses exponential equal jitter and requests a fresh ticket after retry', async () => {
  const originalRandom = Math.random;
  const originalSetTimeout = globalThis.setTimeout;
  const originalClearTimeout = globalThis.clearTimeout;
  const timers = [];
  const tickets = [];
  const sockets = [];
  class FakeSocket {
    static CONNECTING = 0;
    static OPEN = 1;

    constructor(url) {
      this.url = url;
      this.readyState = FakeSocket.OPEN;
      this.sent = [];
      sockets.push(this);
    }

    send(payload) { this.sent.push(payload); }
    close() {}
  }

  try {
    Math.random = () => 0.5;
    globalThis.setTimeout = (callback, delay) => {
      const timer = { callback, delay };
      timers.push(timer);
      return timer;
    };
    globalThis.clearTimeout = () => {};
    globalThis.WebSocket = FakeSocket;
    const client = createClient({ maxReconnectDelayMs: 8000 });
    client.issueTicket = async () => {
      const ticket = `ticket-${tickets.length + 1}`;
      tickets.push(ticket);
      return ticket;
    };
    client.syncMissedMessages = async () => {};
    client.stopped = false;

    client.scheduleReconnect();
    assert.equal(timers[0].delay, 750);
    assert.equal(client.retryCount, 1);
    timers.shift().callback();
    await new Promise((resolve) => originalSetTimeout(resolve, 0));
    assert.equal(tickets.length, 1);
    assert.equal(sockets[0].url, 'ws://localhost/chat/websocket?ticket=ticket-1');

    sockets[0].onclose = undefined;
    client.socket = null;
    client.scheduleReconnect();
    assert.equal(timers[0].delay, 1500);
    timers.shift().callback();
    await new Promise((resolve) => originalSetTimeout(resolve, 0));
    assert.equal(tickets.length, 2);
    assert.equal(sockets[1].url, 'ws://localhost/chat/websocket?ticket=ticket-2');
    client.stop();
  } finally {
    Math.random = originalRandom;
    globalThis.setTimeout = originalSetTimeout;
    globalThis.clearTimeout = originalClearTimeout;
    globalThis.WebSocket = { CONNECTING: 0, OPEN: 1 };
  }
});

test('syncs pages and deduplicates realtime messages by conversationSeq', async () => {
  const received = [];
  const client = createClient({ onMessage: (message) => received.push(message.conversationSeq) });
  client.lastConversationSeq = '5';
  let page = 0;
  client.authorizedFetch = async () => {
    page += 1;
    if (page === 1) {
      return { data: Array.from({ length: 100 }, (_, index) => ({
        conversationSeq: String(index + 6),
      })) };
    }
    return { data: [{ conversationSeq: '105' }, { conversationSeq: '106' }] };
  };
  await client.syncMissedMessages();
  client.socket = { readyState: WebSocket.OPEN };
  client.onFrame(client.socket, JSON.stringify({ type: 'chat', data: { conversationSeq: '8' } }));

  assert.deepEqual(received, Array.from({ length: 101 }, (_, index) => String(index + 6)));
  assert.equal(client.lastConversationSeq, '106');
});
