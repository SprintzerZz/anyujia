import { performance } from 'node:perf_hooks';

const baseUrl = (process.env.BASE_URL || 'http://127.0.0.1:18086').replace(/\/$/, '');
const tokens = (process.env.TOKENS || process.env.TOKEN || '').split(',').map((value) => value.trim()).filter(Boolean);
const taskIds = (process.env.TASK_IDS || '').split(',').map((value) => value.trim()).filter(Boolean);
const target = Number(process.env.SSE_TARGET_VUS || 1000);
const rampSeconds = Number(process.env.SSE_RAMP_SECONDS || 60);
const holdSeconds = Number(process.env.SSE_HOLD_SECONDS || 60);
const userId = process.env.USER_ID || '900001';
const userType = process.env.USER_TYPE || '1';
const userFrom = process.env.USER_FROM || 'app';
const output = process.env.SSE_RESULT_FILE || 'deploy/perf/results/legal-agent-sse-scale.json';

if (!tokens.length) throw new Error('TOKEN or TOKENS is required');
if (taskIds.length < target) throw new Error(`TASK_IDS must contain at least ${target} pending task IDs`);

const results = [];
const startedAt = performance.now();

async function connect(index) {
  const token = tokens[index % tokens.length];
  const taskId = taskIds[index % taskIds.length];
  const controller = new AbortController();
  const requestStartedAt = performance.now();
  const timer = setTimeout(() => controller.abort(), (rampSeconds + holdSeconds + 30) * 1000);
  const result = { index, taskId, status: 0, connectMs: null, durationMs: null, heartbeats: 0, error: null };
  try {
    const response = await fetch(`${baseUrl}/legal/agent/tasks/${taskId}/events`, {
      signal: controller.signal,
      headers: {
        Authorization: `Bearer ${token}`,
        userId,
        userType,
        userFrom,
        Accept: 'text/event-stream',
      },
    });
    result.status = response.status;
    result.connectMs = performance.now() - requestStartedAt;
    if (!response.ok || !response.body) {
      result.error = `HTTP ${response.status}`;
      return result;
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    const deadline = performance.now() + holdSeconds * 1000;
    while (performance.now() < deadline) {
      const read = await reader.read();
      if (read.done) break;
      const text = decoder.decode(read.value, { stream: true });
      result.heartbeats += (text.match(/event:\s*heartbeat/g) || []).length;
    }
    await reader.cancel();
  } catch (error) {
    if (error.name !== 'AbortError') result.error = error.message;
  } finally {
    clearTimeout(timer);
    result.durationMs = performance.now() - requestStartedAt;
  }
  return result;
}

const launchers = Array.from({ length: target }, (_, index) => new Promise((resolve) => {
  setTimeout(() => resolve(connect(index)), Math.floor(index * rampSeconds * 1000 / target));
}));
const settled = await Promise.all(launchers);
results.push(...settled);

function percentile(values, p) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * p) - 1)];
}

const connected = results.filter((item) => item.status === 200);
const summary = {
  generatedAt: new Date().toISOString(),
  target,
  rampSeconds,
  holdSeconds,
  elapsedMs: performance.now() - startedAt,
  connected: connected.length,
  failed: results.length - connected.length,
  connectSuccessRate: results.length ? connected.length / results.length : 0,
  connectP95Ms: percentile(connected.map((item) => item.connectMs), 0.95),
  connectP99Ms: percentile(connected.map((item) => item.connectMs), 0.99),
  heartbeatConnections: connected.filter((item) => item.heartbeats > 0).length,
  results,
};

const fs = await import('node:fs/promises');
await fs.mkdir(new URL('.', `file://${process.cwd()}/${output}`).pathname, { recursive: true }).catch(() => {});
await fs.writeFile(output, JSON.stringify(summary, null, 2));
console.log(JSON.stringify({
  target: summary.target,
  connected: summary.connected,
  failed: summary.failed,
  successRate: summary.connectSuccessRate,
  p95Ms: summary.connectP95Ms,
  p99Ms: summary.connectP99Ms,
  heartbeatConnections: summary.heartbeatConnections,
  output,
}));
