// Feature 2 - "Read a warmed catalog during concurrent purchasing" (Spec 5).
//   MODE=baseline: 50 concurrent clients read the catalog for 60 s (read-only baseline).
//   MODE=mixed:    the same reads while 10 orders per second are submitted for 60 s.
// Target: catalog p95 <= 300 ms with less than 1% server errors; report both results.
import { sleep } from 'k6';
import { browseCatalog, placeOrder, thresholdsFor, summarize, SUMMARY_TREND_STATS } from './lib.js';

const MODE = __ENV.MODE || 'baseline';
const READERS = Number(__ENV.READERS || 50);
const DURATION = __ENV.DURATION || '60s';
const ORDER_RATE = Number(__ENV.ORDER_RATE || 10);
const THINK_TIME = Number(__ENV.THINK_TIME || 0.1); // seconds between two browses of one client

const TARGETS = { 'GET /restaurants': 300, 'GET /restaurants/{id}/menu': 300 };
if (MODE === 'mixed') {
  Object.assign(TARGETS, { 'POST /quotes': null, 'POST /orders': 500 });
}

const scenarios = {
  catalog_readers: {
    executor: 'constant-vus',
    vus: READERS,
    duration: DURATION,
    exec: 'readCatalog',
  },
};
if (MODE === 'mixed') {
  scenarios.purchasers = {
    executor: 'constant-arrival-rate',
    rate: ORDER_RATE,
    timeUnit: '1s',
    duration: DURATION,
    preAllocatedVUs: 20,
    maxVUs: 100,
    exec: 'purchase',
  };
}

export const options = {
  scenarios,
  thresholds: thresholdsFor(TARGETS),
  summaryTrendStats: SUMMARY_TREND_STATS,
  tags: { scenario_group: `catalog-${MODE}` },
};

// Warm the cache (Redis) before measuring, as the scenario requires a warmed catalog.
export function setup() {
  for (let i = 0; i < 3; i++) browseCatalog();
}

export function readCatalog() {
  browseCatalog();
  sleep(THINK_TIME);
}

export function purchase() {
  placeOrder();
}

export function handleSummary(data) {
  const description =
    MODE === 'mixed'
      ? `Feature 2: ${READERS} concurrent catalog readers (think time ${THINK_TIME} s) while ${ORDER_RATE} orders/s are submitted, for ${DURATION}.`
      : `Feature 2 baseline: ${READERS} concurrent catalog readers (think time ${THINK_TIME} s), read-only, for ${DURATION}.`;
  return summarize(data, `catalog-${MODE}`, description, TARGETS);
}
