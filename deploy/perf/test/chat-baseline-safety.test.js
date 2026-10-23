const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const test = require('node:test');

const perfDir = path.resolve(__dirname, '..');
const analyzer = path.join(perfDir, 'chat-baseline-analyze.js');
const runner = path.join(perfDir, 'chat-baseline-diagnose.ps1');

function writeJson(directory, name, value) {
  fs.writeFileSync(path.join(directory, name), `${JSON.stringify(value, null, 2)}\n`);
}

function writeStatus(directory, name, values) {
  fs.writeFileSync(
    path.join(directory, `mysql-status-${name}.tsv`),
    `${Object.entries(values).map(([key, value]) => `${key}\t${value}`).join('\n')}\n`,
  );
}

test('analyzer accepts current flat k6 summary metrics for a healthy stage', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'chat-baseline-analyzer-'));
  writeJson(directory, 'metadata.json', { rates: [100] });
  writeStatus(directory, 'before', { Innodb_log_waits: 0, Innodb_row_lock_time: 0 });
  writeStatus(directory, 'after-100', { Innodb_log_waits: 0, Innodb_row_lock_time: 4 });
  writeJson(directory, 'summary-100.json', {
    metrics: {
      http_req_duration: { 'p(95)': 120, 'p(99)': 240 },
      http_req_failed: { value: 0 },
      dropped_iterations: { count: 0 },
      http_reqs: { count: 6000, rate: 100 },
    },
  });
  writeJson(directory, 'stage-100.json', {
    completed: true,
    containers_healthy_after: true,
    host_healthy_after: true,
    recovery_passed: true,
    snapshot_errors: [],
  });

  const result = spawnSync(process.execPath, [analyzer, directory], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  const diagnosis = JSON.parse(fs.readFileSync(path.join(directory, 'diagnosis.json'), 'utf8'));
  assert.equal(diagnosis.classification, 'SINGLE_MYSQL_NOT_SATURATED');
  assert.equal(diagnosis.physical_sharding_gate_open, false);
  assert.equal(diagnosis.results[0].http_req_duration_p95_ms, 120);
  assert.equal(diagnosis.results[0].valid_run, true);
});

test('runner PlanOnly emits a bounded default plan without contacting Docker', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'chat-baseline-plan-'));
  const shell = process.platform === 'win32' ? 'powershell.exe' : 'pwsh';
  const result = spawnSync(shell, [
    '-NoProfile',
    '-ExecutionPolicy', 'Bypass',
    '-File', runner,
    '-PlanOnly',
    '-OutputDirectory', directory,
  ], { encoding: 'utf8' });

  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
  const plan = JSON.parse(fs.readFileSync(path.join(directory, 'plan.json'), 'utf8'));
  assert.deepEqual(plan.rates, [100, 200, 300, 400, 500]);
  assert.equal(plan.max_vus <= 256, true);
  assert.equal(plan.k6_memory, '512m');
  assert.equal(plan.requires_explicit_1000_opt_in, true);
});

test('analyzer fails closed when the initial MySQL snapshot is missing', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'chat-baseline-invalid-'));
  writeJson(directory, 'metadata.json', { rates: [500, 1000] });
  for (const rate of [500, 1000]) {
    writeStatus(directory, `after-${rate}`, {
      Innodb_log_waits: rate,
      Innodb_row_lock_time: rate * 10,
    });
    writeJson(directory, `summary-${rate}.json`, {
      metrics: {
        http_req_duration: { values: { 'p(95)': 900, 'p(99)': 1500 } },
        http_req_failed: { values: { rate: 0 } },
        dropped_iterations: { values: { count: 0 } },
        http_reqs: { values: { rate } },
      },
    });
    writeJson(directory, `stage-${rate}.json`, {
      completed: true,
      containers_healthy_after: true,
      host_healthy_after: true,
      recovery_passed: true,
      snapshot_errors: [],
    });
  }

  const result = spawnSync(process.execPath, [analyzer, directory], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  const diagnosis = JSON.parse(fs.readFileSync(path.join(directory, 'diagnosis.json'), 'utf8'));
  assert.equal(diagnosis.classification, 'INVALID_RUN');
  assert.equal(diagnosis.physical_sharding_gate_open, false);
  assert.match(diagnosis.results[0].invalid_reasons.join(' '), /mysql-status-before/);
});

test('1000 msg/s plan requires explicit opt-in', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'chat-baseline-opt-in-'));
  const shell = process.platform === 'win32' ? 'powershell.exe' : 'pwsh';
  const common = ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', runner,
    '-PlanOnly', '-OutputDirectory', directory, '-Rates', '1000'];
  const rejected = spawnSync(shell, common, { encoding: 'utf8' });
  assert.notEqual(rejected.status, 0);
  assert.match(`${rejected.stdout}\n${rejected.stderr}`, /requires explicit -Allow1000/);

  const accepted = spawnSync(shell, [...common, '-Allow1000'], { encoding: 'utf8' });
  assert.equal(accepted.status, 0, `${accepted.stdout}\n${accepted.stderr}`);
});

test('analyzer rejects a stage that lost diagnostic evidence', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'chat-baseline-snapshot-'));
  writeJson(directory, 'metadata.json', { rates: [100] });
  writeStatus(directory, 'before', { Innodb_log_waits: 0, Innodb_row_lock_time: 0 });
  writeStatus(directory, 'after-100', { Innodb_log_waits: 0, Innodb_row_lock_time: 0 });
  writeJson(directory, 'summary-100.json', {
    metrics: {
      http_req_duration: { 'p(95)': 100, 'p(99)': 200 },
      http_req_failed: { value: 0 },
      dropped_iterations: { count: 0 },
      http_reqs: { rate: 100 },
    },
  });
  writeJson(directory, 'stage-100.json', {
    completed: true,
    containers_healthy_after: true,
    host_healthy_after: true,
    recovery_passed: true,
    snapshot_errors: ['mysql: I/O error'],
  });

  spawnSync(process.execPath, [analyzer, directory], { encoding: 'utf8' });
  const diagnosis = JSON.parse(fs.readFileSync(path.join(directory, 'diagnosis.json'), 'utf8'));
  assert.equal(diagnosis.classification, 'INVALID_RUN');
  assert.match(diagnosis.results[0].invalid_reasons.join(' '), /snapshot/);
});
