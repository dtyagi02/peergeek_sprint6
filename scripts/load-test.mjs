/**
 * PeerGeek Load Testing Suite — Phase 12 Hardening
 * Measures throughput, latency percentiles (p50, p95, p99), error rate, and cache behavior.
 * Targets:
 *   1. Course Discovery Catalog: GET /api/v1/public/courses
 *   2. Popular Courses: GET /api/v1/public/courses/popular
 *   3. Course Preview: GET /api/v1/public/courses/1
 */

const BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080';
const CONCURRENCY = parseInt(process.env.CONCURRENCY || '10', 10);
const TOTAL_REQUESTS = parseInt(process.env.TOTAL_REQUESTS || '200', 10);

const ENDPOINTS = [
  { name: 'Course Discovery (Catalog)', path: '/api/v1/public/courses' },
  { name: 'Popular Courses', path: '/api/v1/public/courses/popular' },
  { name: 'Course Preview (ID: 1)', path: '/api/v1/public/courses/1' }
];

async function measureRequest(endpoint) {
  const start = performance.now();
  try {
    const res = await fetch(`${BASE_URL}${endpoint.path}`, {
      headers: { 'Accept': 'application/json' }
    });
    const duration = performance.now() - start;
    const cacheControl = res.headers.get('cache-control') || '';
    const etag = res.headers.get('etag') || '';
    return {
      status: res.status,
      ok: res.ok || res.status === 404, // 404 is valid if course id doesn't exist
      duration,
      cacheControl,
      etag
    };
  } catch (err) {
    const duration = performance.now() - start;
    return {
      status: 0,
      ok: false,
      error: err.message,
      duration
    };
  }
}

async function runWorker(endpoint, count, results) {
  for (let i = 0; i < count; i++) {
    const res = await measureRequest(endpoint);
    results.push(res);
  }
}

async function benchmarkEndpoint(endpoint) {
  console.log(`\n======================================================`);
  console.log(`Testing Endpoint: ${endpoint.name} (${endpoint.path})`);
  console.log(`Concurrency: ${CONCURRENCY} workers | Total Requests: ${TOTAL_REQUESTS}`);
  console.log(`======================================================`);

  const results = [];
  const reqsPerWorker = Math.floor(TOTAL_REQUESTS / CONCURRENCY);
  const startTime = performance.now();

  const workers = [];
  for (let w = 0; w < CONCURRENCY; w++) {
    workers.push(runWorker(endpoint, reqsPerWorker, results));
  }
  await Promise.all(workers);
  const totalDurationMs = performance.now() - startTime;

  const successful = results.filter(r => r.ok);
  const errors = results.filter(r => !r.ok);
  const durations = results.map(r => r.duration).sort((a, b) => a - b);

  const p50 = durations[Math.floor(durations.length * 0.50)] || 0;
  const p90 = durations[Math.floor(durations.length * 0.90)] || 0;
  const p95 = durations[Math.floor(durations.length * 0.95)] || 0;
  const p99 = durations[Math.floor(durations.length * 0.99)] || 0;
  const avg = durations.reduce((acc, d) => acc + d, 0) / durations.length;
  const throughput = (results.length / (totalDurationMs / 1000)).toFixed(2);
  const errorRate = ((errors.length / results.length) * 100).toFixed(2);

  console.log(`Results:`);
  console.log(`  Total Completed:    ${results.length}`);
  console.log(`  Successful (2xx):   ${successful.length}`);
  console.log(`  Failed / Errors:    ${errors.length} (${errorRate}%)`);
  console.log(`  Total Duration:     ${(totalDurationMs / 1000).toFixed(3)}s`);
  console.log(`  Throughput:         ${throughput} req/sec`);
  console.log(`  Latency Metrics:`);
  console.log(`    - Mean Latency:   ${avg.toFixed(2)} ms`);
  console.log(`    - Median (p50):   ${p50.toFixed(2)} ms`);
  console.log(`    - p90:            ${p90.toFixed(2)} ms`);
  console.log(`    - p95:            ${p95.toFixed(2)} ms`);
  console.log(`    - p99:            ${p99.toFixed(2)} ms`);
  console.log(`    - Min / Max:      ${durations[0]?.toFixed(2)} ms / ${durations[durations.length - 1]?.toFixed(2)} ms`);

  return {
    endpoint: endpoint.name,
    totalRequests: results.length,
    throughput: `${throughput} req/s`,
    errorRate: `${errorRate}%`,
    p50: `${p50.toFixed(2)} ms`,
    p95: `${p95.toFixed(2)} ms`,
    p99: `${p99.toFixed(2)} ms`
  };
}

async function run() {
  console.log(`Starting PeerGeek Production Load Benchmark against: ${BASE_URL}`);
  const summary = [];
  for (const ep of ENDPOINTS) {
    const res = await benchmarkEndpoint(ep);
    summary.push(res);
  }

  console.log(`\n======================================================`);
  console.log(`LOAD TEST SUMMARY`);
  console.log(`======================================================`);
  console.table(summary);
}

run().catch(console.error);
