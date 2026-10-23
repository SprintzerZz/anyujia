import ws from 'k6/ws';
import { Counter, Rate } from 'k6/metrics';
import { check, sleep } from 'k6';

const url = (__ENV.WS_URL || 'ws://127.0.0.1:18084/chat/websocket').replace(/\/$/, '');
const userIds = (__ENV.USER_IDS || '1,2,3,4').split(',').map((id) => id.trim()).filter(Boolean);
const totalSeconds = Number(__ENV.WS_TOTAL_SECONDS || 60);
const reconnectAttempts = new Counter('chat_reconnect_attempts');
const reconnectSuccesses = new Counter('chat_reconnect_successes');
const heartbeatPongs = new Counter('chat_heartbeat_pongs');
const heartbeatChecks = new Rate('chat_heartbeat_success_rate');

export const options = {
  vus: Number(__ENV.WS_TARGET_VUS || 20),
  duration: `${totalSeconds + 5}s`,
  thresholds: {
    checks: ['rate>0.99'],
  },
};

export default function () {
  const endAt = Date.now() + totalSeconds * 1000;
  let firstConnection = true;
  let gotPong = false;
  while (Date.now() < endAt) {
    reconnectAttempts.add(1);
    gotPong = false;
    const response = ws.connect(url, {
      headers: { userId: userIds[(__VU - 1) % userIds.length], userFrom: 'app' },
    }, (socket) => {
      socket.on('open', () => {
        if (!firstConnection) reconnectSuccesses.add(1);
        firstConnection = false;
        socket.setInterval(() => {
          socket.send(JSON.stringify({ type: 'heart_beat', data: null }));
        }, 2000);
      });
      socket.on('message', (payload) => {
        try {
          const frame = JSON.parse(payload);
          if (frame.type === 'heart_beat' && frame.data === 'pong') {
            gotPong = true;
            heartbeatPongs.add(1);
          }
        } catch (_) { }
      });
      socket.setTimeout(() => socket.close(), Math.min(10000, Math.max(1000, endAt - Date.now())));
    });
    if (!response || response.status !== 101) {
      sleep(1);
      continue;
    }
    check(response, { 'websocket handshake status is 101': (res) => res.status === 101 });
    heartbeatChecks.add(gotPong);
  }
}
