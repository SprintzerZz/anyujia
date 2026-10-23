import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';

const chatHttp = (__ENV.BASE_URL || 'http://127.0.0.1:18083').replace(/\/$/, '');
const chatWs = (__ENV.WS_URL || 'ws://127.0.0.1:18085/chat/websocket').replace(/\/$/, '');
const sessionId = __ENV.SESSION_ID || '900000000000100001';
const senderId = __ENV.SENDER_ID || '1';
const recipientId = __ENV.RECIPIENT_ID || '2';

export const options = {
  scenarios: {
    delivery_ack: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '30s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
  },
};

export default function () {
  let received = 0;
  let acked = 0;

  const response = ws.connect(
    chatWs,
    { headers: { userId: recipientId, userFrom: 'app' } },
    (socket) => {
      socket.on('open', () => {
        const clientMessageId = `delivery-ack-${__VU}-${Date.now()}`;
        const res = http.post(
          `${chatHttp}/chat/message/send`,
          JSON.stringify({ sessionId, clientMessageId, type: 0, content: 'delivery ack test' }),
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
            socket.send(JSON.stringify({ type: 'delivery_ack', data: { messageId: frame.data.messageId } }));
            acked += 1;
          }
        } catch (_) {
          // ignore non-JSON frames
        }
      });

      socket.setTimeout(() => {
        check(received, { 'recipient received chat push': (n) => n >= 1 });
        check(acked, { 'delivery ack sent': (n) => n >= 1 });
        socket.close();
      }, 6000);
    },
  );

  check(response, { 'websocket handshake status is 101': (res) => res && res.status === 101 });
  sleep(1);
}
