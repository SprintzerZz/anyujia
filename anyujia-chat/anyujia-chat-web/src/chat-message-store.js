const DATABASE_NAME = 'anyujia-chat-cache';
const DATABASE_VERSION = 2;
const MESSAGE_STORE = 'messages';
const OUTBOX_STORE = 'outbox';
const SCOPE_INDEX = 'scope';

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains(MESSAGE_STORE)) {
        const store = database.createObjectStore(MESSAGE_STORE, { keyPath: 'cacheKey' });
        store.createIndex(SCOPE_INDEX, 'scope', { unique: false });
      }
      if (!database.objectStoreNames.contains(OUTBOX_STORE)) {
        const store = database.createObjectStore(OUTBOX_STORE, { keyPath: 'cacheKey' });
        store.createIndex(SCOPE_INDEX, 'scope', { unique: false });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function transactionDone(transaction) {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}

function requestResult(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export class ChatMessageStore {
  constructor(maxMessagesPerConversation = 200) {
    this.maxMessagesPerConversation = maxMessagesPerConversation;
    this.databasePromise = openDatabase();
  }

  async loadRecent(userId, sessionId, count = 100) {
    const database = await this.databasePromise;
    const transaction = database.transaction(MESSAGE_STORE, 'readonly');
    const records = await requestResult(
      transaction.objectStore(MESSAGE_STORE).index(SCOPE_INDEX).getAll(this.scope(userId, sessionId)),
    );
    return records
      .sort((left, right) => this.compareSequence(left, right))
      .slice(-count)
      .map(({ cacheKey, scope, ...message }) => message);
  }

  async putMessage(userId, sessionId, message) {
    return this.putMessages(userId, sessionId, [message]);
  }

  async putMessages(userId, sessionId, messages) {
    if (!messages.length) return;
    const database = await this.databasePromise;
    const transaction = database.transaction(MESSAGE_STORE, 'readwrite');
    const done = transactionDone(transaction);
    const store = transaction.objectStore(MESSAGE_STORE);
    const scope = this.scope(userId, sessionId);
    messages.forEach((message) => {
      if (!message?.conversationSeq) return;
      store.put({
        ...message,
        cacheKey: `${scope}:${message.conversationSeq}`,
        scope,
      });
    });
    const records = await requestResult(store.index(SCOPE_INDEX).getAll(scope));
    records
      .sort((left, right) => this.compareSequence(left, right))
      .slice(0, Math.max(0, records.length - this.maxMessagesPerConversation))
      .forEach((record) => store.delete(record.cacheKey));
    await done;
  }

  async clearConversation(userId, sessionId) {
    const database = await this.databasePromise;
    const transaction = database.transaction(MESSAGE_STORE, 'readwrite');
    const done = transactionDone(transaction);
    const store = transaction.objectStore(MESSAGE_STORE);
    const keys = await requestResult(
      store.index(SCOPE_INDEX).getAllKeys(this.scope(userId, sessionId)),
    );
    keys.forEach((key) => store.delete(key));
    await done;
  }

  async loadOutbox(userId, sessionId) {
    const database = await this.databasePromise;
    const transaction = database.transaction(OUTBOX_STORE, 'readonly');
    const records = await requestResult(
      transaction.objectStore(OUTBOX_STORE).index(SCOPE_INDEX).getAll(this.scope(userId, sessionId)),
    );
    return records
      .sort((left, right) => left.createdAt - right.createdAt)
      .map(({ cacheKey, scope, createdAt, ...request }) => request);
  }

  async putOutbox(userId, sessionId, request) {
    const database = await this.databasePromise;
    const transaction = database.transaction(OUTBOX_STORE, 'readwrite');
    const done = transactionDone(transaction);
    const scope = this.scope(userId, sessionId);
    transaction.objectStore(OUTBOX_STORE).put({
      ...request,
      cacheKey: `${scope}:${request.clientMessageId}`,
      scope,
      createdAt: Date.now(),
    });
    await done;
  }

  async deleteOutbox(userId, sessionId, clientMessageId) {
    const database = await this.databasePromise;
    const transaction = database.transaction(OUTBOX_STORE, 'readwrite');
    const done = transactionDone(transaction);
    transaction.objectStore(OUTBOX_STORE)
      .delete(`${this.scope(userId, sessionId)}:${clientMessageId}`);
    await done;
  }

  scope(userId, sessionId) {
    return `${userId}:${sessionId}`;
  }

  compareSequence(left, right) {
    const leftSequence = BigInt(left.conversationSeq);
    const rightSequence = BigInt(right.conversationSeq);
    return leftSequence === rightSequence ? 0 : leftSequence < rightSequence ? -1 : 1;
  }
}
