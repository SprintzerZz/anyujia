import {
  Building2,
  ClipboardCheck,
  createIcons,
  FileText,
  MapPin,
  MessageSquareText,
  PlugZap,
  RefreshCw,
  RotateCcw,
  Save,
  Send,
  ShieldCheck,
  Trash2,
  Unplug,
  WifiOff,
} from 'lucide';
import { ChatRealtimeClient } from '../../../deploy/client/chat-realtime-client.js';
import { ChatMessageStore } from './chat-message-store.js';
import { initHousePublishPage } from './house-publish-page.js';
import { initLegalAgentPanel } from './legal-agent-panel.js';
import './styles.css';

const elements = {
  chatNav: document.querySelector('#chatNavButton'),
  publishNav: document.querySelector('#publishNavButton'),
  chatWorkspace: document.querySelector('#chatWorkspace'),
  publishWorkspace: document.querySelector('#publishWorkspace'),
  badge: document.querySelector('#connectionBadge'),
  connectionText: document.querySelector('#connectionText'),
  token: document.querySelector('#tokenInput'),
  userId: document.querySelector('#userIdInput'),
  sessionId: document.querySelector('#sessionIdInput'),
  sequence: document.querySelector('#sequenceInput'),
  connect: document.querySelector('#connectButton'),
  disconnect: document.querySelector('#disconnectButton'),
  simulate: document.querySelector('#simulateButton'),
  reset: document.querySelector('#resetButton'),
  clearLog: document.querySelector('#clearLogButton'),
  eventLog: document.querySelector('#eventLog'),
  conversationTitle: document.querySelector('#conversationTitle'),
  cursor: document.querySelector('#cursorValue'),
  messageList: document.querySelector('#messageList'),
  emptyState: document.querySelector('#emptyState'),
  composer: document.querySelector('#composer'),
  messageInput: document.querySelector('#messageInput'),
  send: document.querySelector('#sendButton'),
};

let client = null;
let running = false;
let currentUserId = '';
const renderedMessageIds = new Set();
const messageStore = new ChatMessageStore();

elements.token.value = sessionStorage.getItem('chatDebugToken') || '';
elements.userId.value = localStorage.getItem('chatDebugUserId') || '1';
elements.sessionId.value = localStorage.getItem('chatDebugSessionId') || '1';
restoreCursor();
createIcons({
  icons: {
    Building2, ClipboardCheck, FileText, MapPin, MessageSquareText,
    PlugZap, RefreshCw, RotateCcw, Save, Send, ShieldCheck,
    Trash2, Unplug, WifiOff,
  },
});
const housePublishPage = initHousePublishPage();
initLegalAgentPanel({
  tokenInput: elements.token,
  userIdInput: elements.userId,
  sessionIdInput: elements.sessionId,
  addEvent,
});

function showWorkspace(name) {
  const publication = name === 'publish';
  elements.chatWorkspace.hidden = publication;
  elements.publishWorkspace.hidden = !publication;
  elements.chatNav.classList.toggle('nav-active', !publication);
  elements.publishNav.classList.toggle('nav-active', publication);
  elements.badge.hidden = publication;
  window.location.hash = publication ? 'publish' : 'chat';
  if (publication) housePublishPage.activate();
}

function cursorStorageKey() {
  return `chatDebugCursor:${elements.userId.value.trim()}:${elements.sessionId.value.trim()}`;
}

function restoreCursor() {
  const userId = elements.userId.value.trim();
  const sessionId = elements.sessionId.value.trim();
  const key = `chatDebugCursor:${userId}:${sessionId}`;
  elements.sequence.value = localStorage.getItem(key) || '0';
  elements.cursor.textContent = elements.sequence.value;
  elements.conversationTitle.textContent = `会话 #${sessionId || '-'}`;
}

function validateConfiguration() {
  const token = elements.token.value.trim();
  const userId = elements.userId.value.trim();
  const sessionId = elements.sessionId.value.trim();
  const sequence = elements.sequence.value.trim() || '0';
  if (!token) throw new Error('请输入访问令牌');
  if (!/^\d+$/.test(userId)) throw new Error('当前用户 ID 必须是正整数');
  if (!/^\d+$/.test(sessionId)) throw new Error('会话 ID 必须是正整数');
  if (!/^\d+$/.test(sequence)) throw new Error('会话序号必须是非负整数');
  return { token, userId, sessionId, sequence };
}

async function connect() {
  let nextClient = null;
  try {
    const config = validateConfiguration();
    client?.stop();
    clearMessages();
    running = true;
    currentUserId = config.userId;
    sessionStorage.setItem('chatDebugToken', config.token);
    localStorage.setItem('chatDebugUserId', config.userId);
    localStorage.setItem('chatDebugSessionId', config.sessionId);
    elements.conversationTitle.textContent = `会话 #${config.sessionId}`;

    nextClient = new ChatRealtimeClient({
      baseUrl: window.location.origin,
      sessionId: config.sessionId,
      tokenProvider: async () => elements.token.value.trim(),
      onMessage: handleMessage,
      onStateChange: handleConnectionState,
      outboxStore: {
        load: () => messageStore.loadOutbox(config.userId, config.sessionId),
        put: (request) => messageStore.putOutbox(config.userId, config.sessionId, request),
        delete: (clientMessageId) => messageStore.deleteOutbox(
          config.userId,
          config.sessionId,
          clientMessageId,
        ),
      },
      onPersistenceError: (error) => addEvent('待发送消息保存失败', error.message, 'error'),
    });
    client = nextClient;
    setRunningControls(true);
    handleConnectionState('synchronizing');
    addEvent('加载本地消息', `session=${config.sessionId}, count=100`);

    let history = [];
    try {
      history = await messageStore.loadRecent(config.userId, config.sessionId, 100);
    } catch (error) {
      addEvent('本地缓存不可用', '将从服务端加载历史消息', 'error');
    }
    if (!running || client !== nextClient) return;
    if (history.length === 0) {
      addEvent('加载服务端历史', `session=${config.sessionId}, count=100`);
      history = await nextClient.loadRecentMessages(100);
      try {
        await messageStore.putMessages(config.userId, config.sessionId, history);
      } catch (error) {
        addEvent('本地历史保存失败', error.message, 'error');
      }
    } else {
      addEvent('本地消息命中', `count=${history.length}`);
    }
    if (!running || client !== nextClient) return;
    history.forEach((message) => handleMessage(message, false));
    const latestSequence = history.at(-1)?.conversationSeq || '0';
    addEvent('开始连接', `session=${config.sessionId}, afterSeq=${latestSequence}`);
    nextClient.start(latestSequence);
  } catch (error) {
    if (nextClient && client !== nextClient) return;
    running = false;
    nextClient?.stop();
    client = null;
    setRunningControls(false);
    addEvent('连接失败', error.message, 'error');
    setStatus('error', '连接失败');
  }
}

function disconnect() {
  running = false;
  client?.stop();
  client = null;
  setRunningControls(false);
  setStatus('idle', '已断开');
  addEvent('主动断开', '自动重连已停止');
}

function handleConnectionState(state, error) {
  if (!running) return;
  const states = {
    connecting: ['connecting', '正在连接'],
    synchronizing: ['syncing', '同步消息'],
    connected: ['online', '连接正常'],
    disconnected: ['offline', '等待重连'],
    failed: ['error', '连接已停止'],
  };
  const [style, label] = states[state] || ['idle', state];
  setStatus(style, label);
  elements.messageInput.disabled = state !== 'connected';
  elements.send.disabled = state !== 'connected';
  elements.simulate.disabled = state !== 'connected';
  addEvent(label, error?.message || state);
  if (state === 'failed') {
    running = false;
    client = null;
    setRunningControls(false);
  }
}

function handleMessage(message, persist = true) {
  if (!message?.messageId || renderedMessageIds.has(message.messageId)) return;
  renderedMessageIds.add(message.messageId);
  const sequence = String(message.conversationSeq);
  elements.sequence.value = sequence;
  elements.cursor.textContent = sequence;
  localStorage.setItem(cursorStorageKey(), sequence);
  renderMessage(message);
  if (persist) {
    void messageStore.putMessage(currentUserId, message.sessionId, message)
      .catch((error) => addEvent('本地消息保存失败', error.message, 'error'));
  }
}

function renderMessage(message) {
  elements.emptyState.hidden = true;
  const item = document.createElement('article');
  const mine = String(message.fromId) === currentUserId;
  item.className = `message-row ${mine ? 'message-mine' : 'message-other'}`;

  const bubble = document.createElement('div');
  bubble.className = 'message-bubble';
  const content = document.createElement('p');
  content.textContent = message.content || '';
  const meta = document.createElement('div');
  meta.className = 'message-meta';
  const time = Number(message.createTime);
  const formattedTime = Number.isFinite(time)
    ? new Intl.DateTimeFormat('zh-CN', {
        hour: '2-digit', minute: '2-digit', second: '2-digit',
      }).format(new Date(time))
    : '--:--';
  meta.textContent = `seq ${message.conversationSeq} · ${formattedTime}`;
  bubble.append(content, meta);
  item.append(bubble);
  elements.messageList.append(item);
  elements.messageList.scrollTop = elements.messageList.scrollHeight;
}

function sendMessage(event) {
  event.preventDefault();
  const content = elements.messageInput.value.trim();
  if (!content || !client) return;
  const clientMessageId = client.send(content);
  addEvent('发送消息', `clientMessageId=${clientMessageId}`);
  elements.messageInput.value = '';
  resizeComposer();
}

function setStatus(style, label) {
  elements.badge.className = `status-badge status-${style}`;
  elements.connectionText.textContent = label;
}

function setRunningControls(value) {
  elements.connect.disabled = value;
  elements.disconnect.disabled = !value;
  elements.token.disabled = value;
  elements.userId.disabled = value;
  elements.sessionId.disabled = value;
  elements.sequence.disabled = value;
  if (!value) {
    elements.messageInput.disabled = true;
    elements.send.disabled = true;
    elements.simulate.disabled = true;
  }
}

function addEvent(title, detail, level = 'info') {
  const item = document.createElement('li');
  item.className = `event-item event-${level}`;
  const heading = document.createElement('strong');
  heading.textContent = title;
  const description = document.createElement('span');
  description.textContent = detail;
  const time = document.createElement('time');
  time.textContent = new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).format(new Date());
  item.append(heading, description, time);
  elements.eventLog.prepend(item);
  while (elements.eventLog.children.length > 30) {
    elements.eventLog.lastElementChild.remove();
  }
}

function clearMessages() {
  renderedMessageIds.clear();
  elements.messageList.querySelectorAll('.message-row').forEach((node) => node.remove());
  elements.emptyState.hidden = false;
}

async function resetCursor() {
  const userId = elements.userId.value.trim();
  const sessionId = elements.sessionId.value.trim();
  disconnect();
  localStorage.removeItem(cursorStorageKey());
  try {
    await messageStore.clearConversation(userId, sessionId);
  } catch (error) {
    addEvent('本地消息清理失败', error.message, 'error');
  }
  elements.sequence.value = '0';
  elements.cursor.textContent = '0';
  clearMessages();
  addEvent('游标已重置', '下次连接将从 seq=0 开始同步');
}

function resizeComposer() {
  elements.messageInput.style.height = 'auto';
  elements.messageInput.style.height = `${Math.min(elements.messageInput.scrollHeight, 128)}px`;
}

elements.connect.addEventListener('click', connect);
elements.disconnect.addEventListener('click', disconnect);
elements.simulate.addEventListener('click', () => {
  addEvent('模拟中断', '连接将自动重建');
  client?.simulateDisconnect();
});
elements.reset.addEventListener('click', () => void resetCursor());
elements.clearLog.addEventListener('click', () => elements.eventLog.replaceChildren());
elements.composer.addEventListener('submit', sendMessage);
elements.messageInput.addEventListener('input', resizeComposer);
elements.messageInput.addEventListener('keydown', (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    elements.composer.requestSubmit();
  }
});
elements.userId.addEventListener('change', restoreCursor);
elements.sessionId.addEventListener('change', restoreCursor);
elements.chatNav.addEventListener('click', () => showWorkspace('chat'));
elements.publishNav.addEventListener('click', () => showWorkspace('publish'));
window.addEventListener('house-chat-created', (event) => {
  const { token, sessionId } = event.detail || {};
  if (token) {
    elements.token.value = token;
    sessionStorage.setItem('chatDebugToken', token);
    const numericToken = token.replace(/^Bearer\s+/i, '').trim();
    if (/^\d+$/.test(numericToken)) {
      elements.userId.value = numericToken;
      localStorage.setItem('chatDebugUserId', numericToken);
    }
  }
  if (sessionId) {
    elements.sessionId.value = String(sessionId);
    localStorage.setItem('chatDebugSessionId', String(sessionId));
    restoreCursor();
  }
  showWorkspace('chat');
  addEvent('房源详情已创建会话', `session=${sessionId || '-'}`);
});

addEvent('页面就绪', '等待建立连接');
showWorkspace(window.location.hash === '#publish' ? 'publish' : 'chat');
