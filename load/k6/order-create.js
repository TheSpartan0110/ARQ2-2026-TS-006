// Feature 3 - "Accept orders under load with downstream services stopped" (Spec 5).
// 10 valid order requests per second for 60 s; Payments, Restaurant, Delivery and Notifications are stopped by
// load/run-load.ps1. Target: order-creation p95 <= 500 ms and no accepted order without a durable outbox entry
// (checked in the database by the runner after the test).
import { placeOrder, thresholdsFor, summarize, SUMMARY_TREND_STATS } from './lib.js';

const RATE = Number(__ENV.ORDER_RATE || 10);
const DURATION = __ENV.DURATION || '60s';

// The quote is a prerequisite of each order (Catalog), measured but not part of the target.
const TARGETS = { 'POST /quotes': null, 'POST /orders': 500 };

export const options = {
  scenarios: {
    orders: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: thresholdsFor(TARGETS),
  summaryTrendStats: SUMMARY_TREND_STATS,
  tags: { scenario_group: 'order-create' },
};

export default function () {
  placeOrder();
}

export function handleSummary(data) {
  return summarize(
    data,
    'order-create',
    `Feature 3: ${RATE} order requests/s (quote + order) for ${DURATION}, with Payments, Restaurant, Delivery and Notifications stopped.`,
    TARGETS
  );
}
