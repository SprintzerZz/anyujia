export class ChatRealtimeClient {
  constructor({
    baseUrl,
    tokenProvider,
    sessionId,
    onMessage,
    onStateChange,
    outboxStore,
    onPersistenceError,
    heartbeatIntervalMs = 30000,
    pongTimeoutMs = 15000,
    maxReconnectDelayMs = 30000,
  }) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.tokenProvider = tokenProvider;
    this.sessionId = String(sessionId);
    this.onMessage = onMessage || (() => {});
    this.onStateChange = onStateChange || (() => {});
    this.outboxStore = outboxStore;
    this.onPersistenceError = onPersistenceError || (() => {});
    this.heartbeatIntervalMs = heartbeatIntervalMs;
    this.pongTimeoutMs = pongTimeoutMs;
    this.maxReconnectDelayMs = maxReconnectDelayMs;
    this.lastConversationSeq = '0';
    this.outbox = new Map();
    this.pendingMessages = [];
    this.synchronizing = false;
    this.retryCount = 0;
    this.socket = null;
    this.reconnectTimer = null;
    this.heartbeatTimer = null;
    this.pongTimer = null;
    this.outboxWrite = Promise.resolve();
    this.connecting = false;
    this.networkListenersAttached = false;
    this.handleOnline = () => this.reconnectNow();
    this.handleOffline = () => {
      if (this.socket?.readyState === WebSocket.OPEN) {
        this.socket.close(4002, 'Browser reported offline');
      }
    };
    this.stopped = true;
  }

  start(afterConversationSeq = '0') {
    this.lastConversationSeq = String(afterConversationSeq);
    this.stopped = false;
    this.attachNetworkListeners();
    void this.restoreOutboxAndConnect();
  }

  stop() {
    this.stopped = true;
    clearTimeout(this.reconnectTimer);
    clearInterval(this.heartbeatTimer);
    clearTimeout(this.pongTimer);
    this.detachNetworkListeners();
    if (this.socket) {
      this.socket.close(1000, 'Client stopped');
    }
  }

  simulateDisconnect() {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.close(4002, 'Simulated network interruption');
    }
  }

  send(content, type = 0, clientMessageId = crypto.randomUUID()) {
    const request = {
      sessionId: this.sessionId,
      clientMessageId,
      type,
      content,
    };
    this.outbox.set(clientMessageId, request);
    this.queueOutboxWrite(() => this.outboxStore?.put(request));
    this.sendFrame({ type: 'chat', data: request });
    return clientMessageId;
  }

  async connect() {
    if (this.stopped || this.connecting) return;
    if (this.socket?.readyState === WebSocket.CONNECTING
      || this.socket?.readyState === WebSocket.OPEN) return;
    this.connecting = true;
    this.onStateChange('connecting');
    try {
      const ticket = await this.issueTicket();
      if (this.stopped) return;
      const websocketUrl = this.baseUrl.replace(/^http/, 'ws');
      const socket = new WebSocket(
        `${websocketUrl}/chat/websocket?ticket=${encodeURIComponent(ticket)}`,
      );
      this.socket = socket;
      socket.onopen = () => this.onOpen(socket);
      socket.onmessage = (event) => this.onFrame(socket, event.data);
      socket.onerror = () => {
        // The close event carries the actionable status. Heartbeat covers half-open sockets.
      };
      socket.onclose = (event) => this.onClose(socket, event);
    } catch (error) {
      if (this.shouldRetryRequest(error)) {
        this.onStateChange('disconnected', error);
        this.scheduleReconnect();
      } else {
        this.stopped = true;
        this.onStateChange('failed', error);
      }
    } finally {
      this.connecting = false;
    }
  }

  async onOpen(socket) {
    if (socket !== this.socket) return;
    this.onStateChange('synchronizing');
    this.startHeartbeat(socket);
    try {
      this.synchronizing = true;
      await this.syncMissedMessages();
      this.synchronizing = false;
      this.drainPendingMessages();
      this.retryCount = 0;
      this.onStateChange('connected');
      for (const request of this.outbox.values()) {
        this.sendFrame({ type: 'chat', data: request });
      }
    } catch (error) {
      this.synchronizing = false;
      this.failConnection(socket, error);
    }
  }

  onFrame(socket, payload) {
    if (socket !== this.socket) return;
    let frame;
    try {
      frame = JSON.parse(payload);
    } catch (error) {
      return;
    }
    if (frame.type === 'heart_beat' && frame.data === 'pong') {
      clearTimeout(this.pongTimer);
      this.pongTimer = null;
      return;
    }
    if (frame.type !== 'chat' && frame.type !== 'chat_ack') return;
    const message = frame.data;
    if (frame.type === 'chat_ack' && message.clientMessageId) {
      this.outbox.delete(message.clientMessageId);
      this.queueOutboxWrite(() => this.outboxStore?.delete(message.clientMessageId));
    }
    if (this.synchronizing) {
      this.pendingMessages.push(message);
    } else if (this.hasSequenceGap(message)) {
      this.pendingMessages.push(message);
      this.recoverSequenceGap(socket);
    } else {
      this.acceptMessage(message);
    }
  }

  hasSequenceGap(message) {
    if (!message?.conversationSeq) return false;
    return BigInt(message.conversationSeq) > BigInt(this.lastConversationSeq) + 1n;
  }

  async recoverSequenceGap(socket) {
    if (this.synchronizing || socket !== this.socket) return;
    this.synchronizing = true;
    this.onStateChange('synchronizing');
    try {
      await this.syncMissedMessages();
      this.synchronizing = false;
      this.drainPendingMessages();
      this.onStateChange('connected');
    } catch (error) {
      this.synchronizing = false;
      this.failConnection(socket, error);
    }
  }

  onClose(socket, event) {
    if (socket !== this.socket) return;
    clearInterval(this.heartbeatTimer);
    clearTimeout(this.pongTimer);
    this.heartbeatTimer = null;
    this.pongTimer = null;
    this.socket = null;
    if (this.stopped) return;
    const error = new Error(
      `WebSocket closed: code=${event.code}${event.reason ? `, reason=${event.reason}` : ''}`,
    );
    if (this.shouldRetryClose(event.code)) {
      this.onStateChange('disconnected', error);
      this.scheduleReconnect();
    } else {
      this.stopped = true;
      this.onStateChange('failed', error);
    }
  }

  startHeartbeat(socket) {
    clearInterval(this.heartbeatTimer);
    clearTimeout(this.pongTimer);
    const ping = () => {
      if (socket !== this.socket || socket.readyState !== WebSocket.OPEN) return;
      this.sendFrame({ type: 'heart_beat', data: null });
      clearTimeout(this.pongTimer);
      this.pongTimer = setTimeout(() => {
        if (socket === this.socket) socket.close(4000, 'Heartbeat timeout');
      }, this.pongTimeoutMs);
    };
    ping();
    this.heartbeatTimer = setInterval(ping, this.heartbeatIntervalMs);
  }

  scheduleReconnect() {
    if (this.stopped || this.reconnectTimer) return;
    const cap = Math.min(1000 * 2 ** this.retryCount, this.maxReconnectDelayMs);
    const delay = cap / 2 + Math.random() * cap / 2;
    this.retryCount += 1;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
  }

  async issueTicket() {
    const response = await this.authorizedFetch('/chat/websocket/ticket', {
      method: 'POST',
    });
    return response.data.ticket;
  }

  async loadRecentMessages(count = 100) {
    const response = await this.authorizedFetch('/chat/message/list', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        sessionId: this.sessionId,
        count,
        needCurMessage: false,
      }),
    });
    return response.data || [];
  }

  async syncMissedMessages() {
    const count = 100;
    for (;;) {
      const response = await this.authorizedFetch('/chat/message/sync', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId: this.sessionId,
          afterConversationSeq: this.lastConversationSeq,
          count,
        }),
      });
      const messages = response.data || [];
      messages.forEach((message) => this.acceptMessage(message));
      if (messages.length < count) return;
    }
  }

  acceptMessage(message) {
    if (!message?.conversationSeq) return;
    if (BigInt(message.conversationSeq) <= BigInt(this.lastConversationSeq)) return;
    this.lastConversationSeq = message.conversationSeq;
    this.onMessage(message);
  }

  drainPendingMessages() {
    this.pendingMessages
      .sort((left, right) => {
        const leftSeq = BigInt(left.conversationSeq);
        const rightSeq = BigInt(right.conversationSeq);
        return leftSeq === rightSeq ? 0 : leftSeq < rightSeq ? -1 : 1;
      })
      .forEach((message) => this.acceptMessage(message));
    this.pendingMessages = [];
  }

  sendFrame(frame) {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(frame));
    }
  }

  async authorizedFetch(path, options) {
    const token = await this.tokenProvider();
    const response = await fetch(`${this.baseUrl}${path}`, {
      ...options,
      headers: {
        ...options.headers,
        Authorization: `Bearer ${token}`,
      },
    });
    let body;
    try {
      body = await response.json();
    } catch (error) {
      throw new ChatRequestError(`Chat request returned invalid JSON: HTTP ${response.status}`, response.status);
    }
    if (!response.ok || body.code !== 200000) {
      throw new ChatRequestError(
        `Chat request failed: HTTP ${response.status}, code ${body.code}`,
        response.status,
        body.code,
      );
    }
    return body;
  }

  async restoreOutboxAndConnect() {
    try {
      const requests = await this.outboxStore?.load();
      requests?.forEach((request) => this.outbox.set(request.clientMessageId, request));
    } catch (error) {
      this.onPersistenceError(error);
    }
    if (!this.stopped) await this.connect();
  }

  queueOutboxWrite(operation) {
    if (!this.outboxStore) return;
    this.outboxWrite = this.outboxWrite
      .then(operation)
      .catch((error) => this.onPersistenceError(error));
  }

  failConnection(socket, error) {
    if (this.shouldRetryRequest(error)) {
      socket.close(4001, 'Message synchronization failed');
      return;
    }
    this.stopped = true;
    this.onStateChange('failed', error);
    socket.close(4003, 'Authentication or request rejected');
  }

  shouldRetryRequest(error) {
    if (!(error instanceof ChatRequestError)) return true;
    const businessStatus = Number(String(error.code || '').slice(0, 3));
    const status = error.status >= 400 ? error.status : businessStatus;
    return status === 408 || status === 425 || status === 429 || status >= 500;
  }

  shouldRetryClose(code) {
    return code !== 1000 && code !== 4003 && code !== 4004;
  }

  reconnectNow() {
    if (this.stopped || this.socket) return;
    clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
    void this.connect();
  }

  attachNetworkListeners() {
    if (this.networkListenersAttached || !globalThis.addEventListener) return;
    globalThis.addEventListener('online', this.handleOnline);
    globalThis.addEventListener('offline', this.handleOffline);
    this.networkListenersAttached = true;
  }

  detachNetworkListeners() {
    if (!this.networkListenersAttached || !globalThis.removeEventListener) return;
    globalThis.removeEventListener('online', this.handleOnline);
    globalThis.removeEventListener('offline', this.handleOffline);
    this.networkListenersAttached = false;
  }
}

export class ChatRequestError extends Error {
  constructor(message, status, code) {
    super(message);
    this.name = 'ChatRequestError';
    this.status = status;
    this.code = code;
  }
}
