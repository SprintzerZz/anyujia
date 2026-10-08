import { check, fail } from 'k6';
import http from 'k6/http';
import ws from 'k6/ws';

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '');
const webSocketBaseUrl = baseUrl.replace(/^http/, 'ws');
const token = __ENV.TOKEN;

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1'],
  },
};

export default function () {
  if (!token) {
    fail('TOKEN is required');
  }

  const ticketResponse = http.post(`${baseUrl}/chat/websocket/ticket`, null, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (ticketResponse.status !== 200 || ticketResponse.json('code') !== 200000) {
    fail(`ticket request failed: status=${ticketResponse.status}, body=${ticketResponse.body}`);
  }
  const ticket = ticketResponse.json('data.ticket');

  let receivedPong = false;
  const response = ws.connect(
    `${webSocketBaseUrl}/chat/websocket?ticket=${encodeURIComponent(ticket)}`,
    null,
    (socket) => {
      socket.on('open', () => {
        socket.send(JSON.stringify({ type: 'heart_beat', data: null }));
      });

      socket.on('message', (payload) => {
        const frame = JSON.parse(payload);
        if (frame.type === 'heart_beat' && frame.data === 'pong') {
          receivedPong = true;
          socket.close();
        }
      });

      socket.on('error', (error) => {
        console.error(`websocket error: ${error.error()}`);
      });

      socket.setTimeout(() => socket.close(), 5000);
    },
  );

  check(response, {
    'websocket handshake status is 101': (res) => res && res.status === 101,
  });
  check(receivedPong, {
    'heartbeat receives pong': (value) => value,
  });
}
