import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';

const chatHttp = (__ENV.BASE_URL || 'http://127.0.0.1:18083').replace(/\/$/, '');
const chatWs = (__ENV.WS_URL || 'ws://127.0.0.1:18085/chat/websocket').replace(/\/$/, '');
const sessionId = __ENV.SESSION_ID || '900000000000100001';
const senderId = __ENV.SENDER_ID || '1';
const recipientId = __ENV.RECIPIENT_ID || '2';

// Recipient stays online but never acknowledges, so the reaper re-pushes the message
// (default 3 retries) and then dead-letters it.
export const options = {
  scenarios: {
    delivery_retry: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '40s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
  },
};

export default function () {
  let received = 0;

  const response = ws.connect(
    chatWs,
    { headers: { userId: recipientId, userFrom: 'app' } },
    (socket) => {
      socket.on('open', () => {
        const clientMessageId = `delivery-retry-${__VU}-${Date.now()}`;
        const res = http.post(
          `${chatHttp}/chat/message/send`,
          JSON.stringify({ sessionId, clientMessageId, type: 0, content: 'delivery retry test' }),
          { headers: { 'Content-Type': 'application/json', userId: senderId, userFrom: 'app' } },
        );
        check(res, {
          'message sent HTTP 200': (r) => r.status === 200 && r.json('code') === 200000,
        });
      });

      socket.on('message', (payload) => {
        try {
          const frame = JSON.parse(payload);
          if (frame.type === 'chat') {
            received += 1;
          }
        } catch (_) {
          // ignore non-JSON frames
        }
      });

      socket.setTimeout(() => {
        // initial push + 3 retries before dead-letter
        check(received, { 'recipient received initial push plus retries (>=4)': (n) => n >= 4 });
        socket.close();
      }, 26000);
    },
  );

  check(response, { 'websocket handshake status is 101': (res) => res && res.status === 101 });
  sleep(1);
}
