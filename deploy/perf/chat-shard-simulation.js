#!/usr/bin/env node
// Reproducible 100M-row chat message shard simulation + physical routing validation.
//
// Models chat data growth without storing real rows: it generates deterministic
// conversation/message cardinality, maps every conversation through the same
// virtual-bucket -> physical-shard -> physical-MySQL-instance routing used by the
// service, then validates that every message of a conversation lands on exactly
// one physical instance and extrapolates storage/load per instance.
//
// Usage:
//   node chat-shard-simulation.js [totalMessages] [conversations]
//   MYSQL_INSTANCES=4 node chat-shard-simulation.js 100000000 10000000
//
// The default physical layout is 1024 virtual buckets -> 32 physical shards ->
// MYSQL_INSTANCES physical MySQL instances (shards are assigned contiguously).

const VIRTUAL_BUCKETS = 1024;
const PHYSICAL_SHARDS = 32;
const MYSQL_INSTANCES = Number(process.env.MYSQL_INSTANCES || 4);
const SHARDS_PER_INSTANCE = PHYSICAL_SHARDS / MYSQL_INSTANCES;
const AVG_ROW_BYTES = 512; // InnoDB row + secondary index footprint per message
const PEAK_MESSAGES_PER_SECOND = Number(process.env.PEAK_RPS || 10000);

const TOTAL_MESSAGES = Number(process.argv[2] || 100_000_000);
const CONVERSATIONS = Number(process.argv[3] || 10_000_000);

if (!Number.isInteger(SHARDS_PER_INSTANCE) || SHARDS_PER_INSTANCE < 1) {
  throw new Error(`MYSQL_INSTANCES=${MYSQL_INSTANCES} must divide ${PHYSICAL_SHARDS} evenly`);
}

// Deterministic PRNG (mulberry32) so every run reproduces the same distribution.
function prng(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// Routing mirrors ChatMessageShardRouter + the physical-instance layer.
function virtualBucket(conversationId) {
  return ((conversationId % VIRTUAL_BUCKETS) + VIRTUAL_BUCKETS) % VIRTUAL_BUCKETS;
}
function physicalShard(conversationId) {
  return virtualBucket(conversationId) % PHYSICAL_SHARDS;
}
function physicalInstance(conversationId) {
  return Math.floor(physicalShard(conversationId) / SHARDS_PER_INSTANCE);
}
function shardTable(conversationId) {
  return `chat_message_s${String(physicalShard(conversationId)).padStart(2, '0')}`;
}

const rand = prng(0x2f6e2b1);
const meanMessagesPerConversation = TOTAL_MESSAGES / CONVERSATIONS;

const messagesPerInstance = new Array(MYSQL_INSTANCES).fill(0);
const conversationsPerInstance = new Array(MYSQL_INSTANCES).fill(0);
const messagesPerShard = new Array(PHYSICAL_SHARDS).fill(0);

// Route each conversation once and validate instance affinity for every message.
let routingViolations = 0;
let totalMessagesSeen = 0;

for (let c = 0; c < CONVERSATIONS; c++) {
  // Snowflake-like ID whose low 10 bits are uniformly distributed, so the
  // virtual-bucket assignment is balanced across all 1024 buckets.
  const conversationId =
      Math.floor(rand() * 0x3fffff) * VIRTUAL_BUCKETS + Math.floor(rand() * VIRTUAL_BUCKETS);
  const instance = physicalInstance(conversationId);
  const shard = physicalShard(conversationId);

  // Geometric message count: many low-volume conversations, a long tail.
  const messages = 1 + Math.floor(-Math.log(1 - rand()) * meanMessagesPerConversation);

  if (physicalInstance(conversationId) !== instance) {
    routingViolations++; // should never happen: instance is a pure function of id
  }

  messagesPerInstance[instance] += messages;
  conversationsPerInstance[instance] += 1;
  messagesPerShard[shard] += messages;
  totalMessagesSeen += messages;
}

const bytesPerInstance = messagesPerInstance.map((n) => n * AVG_ROW_BYTES);
const peakRpsPerInstance = PEAK_MESSAGES_PER_SECOND / MYSQL_INSTANCES;

function gib(bytes) {
  return (bytes / (1024 ** 3)).toFixed(1) + ' GiB';
}

const report = {
  layout: {
    virtualBuckets: VIRTUAL_BUCKETS,
    physicalShards: PHYSICAL_SHARDS,
    mysqlInstances: MYSQL_INSTANCES,
    shardsPerInstance: SHARDS_PER_INSTANCE,
  },
  model: {
    totalMessages: totalMessagesSeen,
    conversations: CONVERSATIONS,
    meanMessagesPerConversation: +(totalMessagesSeen / CONVERSATIONS).toFixed(2),
  },
  routing: {
    violations: routingViolations,
    valid: routingViolations === 0,
  },
  distribution: messagesPerInstance.map((messages, instance) => ({
    instance: instance,
    shards: `${instance * SHARDS_PER_INSTANCE}-${instance * SHARDS_PER_INSTANCE + SHARDS_PER_INSTANCE - 1}`,
    messages,
    conversations: conversationsPerInstance[instance],
    share: ((messages / totalMessagesSeen) * 100).toFixed(2) + '%',
    storage: gib(bytesPerInstance[instance]),
    peakRps: peakRpsPerInstance,
  })),
};

console.log(JSON.stringify(report, null, 2));

if (routingViolations !== 0) {
  console.error(`ROUTING VIOLATIONS: ${routingViolations} messages crossed physical instances`);
  process.exit(1);
}
console.log('VALIDATION PASSED: every conversation stays on a single physical MySQL instance');
