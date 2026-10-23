import http from 'k6/http';
import ws from 'k6/ws';
import { check, fail, sleep } from 'k6';

const ticketBaseUrl = (__ENV.TICKET_BASE_URL || __ENV.BASE_URL || 'http://127.0.0.1:18083').replace(/\/$/, '');
const webSocketBaseUrl = (__ENV.WS_URL || 'ws://127.0.0.1:18085').replace(/\/$/, '');
const tokens = (__ENV.TOKENS || __ENV.TOKEN || '').split(',').map((token) => token.trim()).filter(Boolean);
const targetVus = Number(__ENV.WS_TARGET_VUS || 10000);
const rampDuration = __ENV.WS_RAMP_DURATION || '10m';
const holdDuration = __ENV.WS_HOLD_DURATION || '20m';
const heartbeatIntervalSeconds = Number(__ENV.WS_HEARTBEAT_SECONDS || 30);
const sessionSeconds = Number(__ENV.WS_SESSION_SECONDS || Math.max(heartbeatIntervalSeconds * 2, 60));
const minSessionMs = Math.max(1000, (sessionSeconds - Math.min(5, sessionSeconds / 2)) * 1000);

export const options = {
  scenarios: {
    chat_websocket_scale: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { target: targetVus, duration: rampDuration },
        { target: targetVus, duration: holdDuration },
        { target: 0, duration: '2m' },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    ws_connecting: ['p(95)<1000', 'p(99)<3000'],
    ws_session_duration: [`p(95)>${minSessionMs}`],
  },
};

function tokenForVu() {
  if (!tokens.length) {
    fail('TOKENS or TOKEN environment variable is required');
  }
  return tokens[(__VU - 1) % tokens.length];
}

function issueTicket(token) {
  const headers = { Authorization: `Bearer ${token}` };
  if (__ENV.TICKET_USER_ID) headers.userId = __ENV.TICKET_USER_ID;
  if (__ENV.TICKET_USER_NAME) headers.userName = __ENV.TICKET_USER_NAME;
  if (__ENV.TICKET_USER_FROM) headers.userFrom = __ENV.TICKET_USER_FROM;
  if (__ENV.TICKET_USER_TYPE) headers.userType = __ENV.TICKET_USER_TYPE;
  const response = http.post(`${ticketBaseUrl}/chat/websocket/ticket`, null, {
    headers,
    tags: { api: 'chat-websocket-ticket' },
  });
  if (response.status !== 200 || response.json('code') !== 200000) {
    fail(`ticket failed: status=${response.status}, body=${response.body}`);
  }
  return response.json('data.ticket');
}

export default function () {
  const token = tokenForVu();
  const ticket = issueTicket(token);
  const response = ws.connect(
    `${webSocketBaseUrl}/chat/websocket?ticket=${encodeURIComponent(ticket)}`,
    { tags: { api: 'chat-websocket-scale' } },
    (socket) => {
      let pongCount = 0;
      socket.on('open', () => {
        socket.setInterval(() => {
          socket.send(JSON.stringify({ type: 'heart_beat', data: null }));
        }, heartbeatIntervalSeconds * 1000);
      });
      socket.on('message', (payload) => {
        try {
          const frame = JSON.parse(payload);
          if (frame.type === 'heart_beat' && frame.data === 'pong') {
            pongCount += 1;
          }
        } catch (_) {
          // Non-JSON frames are counted by the final check as missing pongs.
        }
      });
      socket.setTimeout(() => {
        check(pongCount, { 'received heartbeat pong': (count) => count > 0 });
        socket.close();
      }, sessionSeconds * 1000);
    },
  );
  check(response, {
    'websocket handshake status is 101': (res) => res && res.status === 101,
  });
  sleep(1);
}
