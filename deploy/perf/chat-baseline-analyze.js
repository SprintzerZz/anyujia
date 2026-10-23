const fs = require('node:fs');
const path = require('node:path');

const outputDir = process.argv[2];
if (!outputDir) throw new Error('Usage: node chat-baseline-analyze.js <output-directory>');

function readJson(name) {
  const file = path.join(outputDir, name);
  if (!fs.existsSync(file)) return { value: null, error: `missing ${name}` };
  try {
    return { value: JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '')), error: null };
  } catch (error) {
    return { value: null, error: `invalid ${name}: ${error.message}` };
  }
}

function readStatus(name) {
  const values = {};
  const fileName = `mysql-status-${name}.tsv`;
  const file = path.join(outputDir, fileName);
  if (!fs.existsSync(file)) return { values, error: `missing ${fileName}` };
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const [key, rawValue] = line.replace(/^\uFEFF/, '').split('\t');
    if (key && Number.isFinite(Number(rawValue))) values[key] = Number(rawValue);
  }
  return { values, error: null };
}

function readDataSourcePools(name) {
  const file = path.join(outputDir, `datasource-pools-${name}.json`);
  if (!fs.existsSync(file)) return { value: null, error: `missing datasource-pools-${name}.json` };
  try {
    return { value: JSON.parse(fs.readFileSync(file, 'utf8').replace(/^﻿/, '')), error: null };
  } catch (error) {
    return { value: null, error: `invalid datasource-pools-${name}.json: ${error.message}` };
  }
}

function summarizePools(pools) {
  if (!pools) return null;
  const detail = Object.entries(pools).map(([pool, value]) => {
    const active = Number(value.active || 0);
    const idle = Number(value.idle || 0);
    const pending = Number(value.pending || 0);
    const max = Number(value.maximumPoolSize || 0);
    return {
      pool,
      active,
      idle,
      pending,
      max,
      connectionTimeoutMillis: Number(value.connectionTimeoutMillis || 0),
      activeRatio: max > 0 ? Number((active / max).toFixed(3)) : 0,
    };
  });
  return {
    detail,
    maxPending: detail.reduce((m, p) => Math.max(m, p.pending), 0),
    maxActiveRatio: detail.reduce((m, p) => Math.max(m, p.activeRatio), 0),
  };
}

function metric(summary, name, key) {
  const value = summary?.metrics?.[name];
  if (!value) return null;
  const candidate = value.values?.[key] ?? value[key] ?? (key === 'rate' ? value.value : undefined);
  return Number.isFinite(Number(candidate)) ? Number(candidate) : null;
}

const metadataResult = readJson('metadata.json');
if (!metadataResult.value || !Array.isArray(metadataResult.value.rates)) {
  throw new Error(metadataResult.error || 'metadata.json must contain a rates array');
}

const metadata = metadataResult.value;
const beforeResult = readStatus('before');
const results = [];
let previous = beforeResult.values;

for (const rate of metadata.rates) {
  const summaryResult = readJson(`summary-${rate}.json`);
  const stageResult = readJson(`stage-${rate}.json`);
  const afterResult = readStatus(`after-${rate}`);
  const poolsAfter = summarizePools(readDataSourcePools(`after-${rate}`).value);
  const invalidReasons = [beforeResult.error, summaryResult.error, stageResult.error, afterResult.error].filter(Boolean);
  const summary = summaryResult.value;
  const stage = stageResult.value;
  if (stage && stage.completed !== true) invalidReasons.push('stage did not complete');
  if (stage && stage.containers_healthy_after !== true) invalidReasons.push('containers were unhealthy after stage');
  if (stage && stage.host_healthy_after !== true) invalidReasons.push('host was unhealthy after stage');
  if (stage && stage.recovery_passed !== true) invalidReasons.push('recovery gate did not pass');
  if (stage && (!Array.isArray(stage.snapshot_errors) || stage.snapshot_errors.length > 0)) {
    invalidReasons.push('stage diagnostic snapshot was incomplete');
  }

  const delta = (name) => Math.max(0, (afterResult.values[name] || 0) - (previous[name] || 0));
  const p95 = metric(summary, 'http_req_duration', 'p(95)');
  const p99 = metric(summary, 'http_req_duration', 'p(99)');
  const errorRate = metric(summary, 'http_req_failed', 'rate');
  const dropped = metric(summary, 'dropped_iterations', 'count') || 0;
  const achievedRate = metric(summary, 'http_reqs', 'rate');
  const dbPressure = delta('Innodb_buffer_pool_wait_free') > 0
    || delta('Innodb_log_waits') > 0
    || delta('Innodb_row_lock_time') > 1000;
  const metricsComplete = [p95, p99, errorRate, achievedRate].every((value) => value !== null);
  if (!metricsComplete) invalidReasons.push('required k6 metrics are missing');
  const validRun = invalidReasons.length === 0;
  const sloPassed = validRun && p95 < 500 && p99 < 1200 && errorRate < 0.01
    && dropped === 0 && achievedRate >= rate * 0.95;

  results.push({
    rate,
    valid_run: validRun,
    invalid_reasons: invalidReasons,
    slo_passed: sloPassed,
    achieved_rate: achievedRate,
    target_rate_attainment: achievedRate === null ? null : achievedRate / rate,
    http_req_duration_p95_ms: p95,
    http_req_duration_p99_ms: p99,
    http_error_rate: errorRate,
    dropped_iterations: dropped,
    business_msg_per_sec: stage?.business_msg_per_sec ?? null,
    shard_msg_per_sec: stage?.shard_msg_per_sec ?? null,
    business_writes: stage?.business_writes ?? null,
    shard_writes: stage?.shard_writes ?? null,
    business_attainment: (stage?.business_msg_per_sec != null && rate > 0)
      ? stage.business_msg_per_sec / rate : null,
    connection_pool_saturated: poolsAfter ? poolsAfter.maxPending > 0 : null,
    connection_pool: poolsAfter,
    shard_writes_per_instance: Array.isArray(stage?.shard_writes_per_instance)
      ? stage.shard_writes_per_instance : null,
    mysql_delta: {
      buffer_pool_wait_free: delta('Innodb_buffer_pool_wait_free'),
      log_waits: delta('Innodb_log_waits'),
      row_lock_time_ms: delta('Innodb_row_lock_time'),
      row_lock_waits: delta('Innodb_row_lock_waits'),
      fsyncs: delta('Innodb_data_fsyncs'),
      os_log_written_bytes: delta('Innodb_os_log_written'),
      inserts: delta('Com_insert'),
      updates: delta('Com_update'),
    },
    db_pressure_signal: dbPressure,
  });
  previous = afterResult.values;
}

const invalid = results.filter((result) => !result.valid_run);
const failed = results.filter((result) => result.valid_run && !result.slo_passed);
const requiredGateRatesPresent = [500, 1000].every((rate) => results.some((result) => result.rate === rate));
const dbConfirmed = invalid.length === 0 && requiredGateRatesPresent && failed.length > 0
  && failed.every((result) => result.db_pressure_signal);
const classification = invalid.length > 0 ? 'INVALID_RUN'
  : failed.length === 0 ? 'SINGLE_MYSQL_NOT_SATURATED'
    : dbConfirmed ? 'SINGLE_MYSQL_CONFIRMED' : 'INCONCLUSIVE_OR_NON_DB';

const businessRates = results.map((r) => r.business_msg_per_sec).filter((v) => v != null);
const diagnosis = {
  generated_at: new Date().toISOString(),
  classification,
  physical_sharding_gate_open: dbConfirmed,
  required_gate_rates_present: requiredGateRatesPresent,
  max_business_msg_per_sec: businessRates.length ? Math.max(...businessRates) : null,
  business_throughput: results
    .filter((r) => r.business_msg_per_sec != null)
    .map((r) => ({ rate: r.rate, business_msg_per_sec: r.business_msg_per_sec,
      shard_msg_per_sec: r.shard_msg_per_sec, http_req_per_sec: r.achieved_rate,
      business_attainment: r.business_attainment })),
  connection_pool_saturation: results
    .filter((r) => r.connection_pool)
    .map((r) => ({ rate: r.rate, saturated: r.connection_pool_saturated,
      max_pending: r.connection_pool.maxPending,
      max_active_ratio: r.connection_pool.maxActiveRatio,
      pools: r.connection_pool.detail })),
  shard_write_distribution: results
    .filter((r) => Array.isArray(r.shard_writes_per_instance))
    .map((r) => ({ rate: r.rate, total_shard_writes: r.shard_writes,
      per_instance: r.shard_writes_per_instance })),
  results,
  interpretation: classification === 'INVALID_RUN'
    ? 'Run evidence is incomplete or the environment was unhealthy. Discard the run and keep physical sharding closed.'
    : dbConfirmed
      ? 'Valid 500/1000 evidence shows SLO failure coinciding with InnoDB buffer/log/row-lock pressure.'
      : 'Do not implement physical sharding from this run; inspect application, queue, Redis, container and SQL-digest evidence.',
};
fs.writeFileSync(path.join(outputDir, 'diagnosis.json'), `${JSON.stringify(diagnosis, null, 2)}\n`);
process.stdout.write(`${JSON.stringify(diagnosis, null, 2)}\n`);
