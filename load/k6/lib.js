// Shared helpers of the EatsNow load scenarios (Spec 5, Features 2 and 3; evidence per Spec 6.7).
// The access token comes from the real OIDC login (scripts/oidc-login.ps1), run by load/run-load.ps1.
import http from 'k6/http';
import { check } from 'k6';

export const GATEWAY = __ENV.GATEWAY || 'http://gateway:8080';
export const TOKEN = __ENV.TOKEN;

// Seed data (Catalog V1 migration): Burger House / Classic Burger. It has no restaurant user, so load orders
// never show up in the demo restaurant's inbox.
export const RESTAURANT_ID = '00000000-0000-4000-8000-000000000002';
export const MENU_ITEM_ID = '00000000-0000-4000-8000-000000000003';

if (!TOKEN) {
  throw new Error('TOKEN is not set: run the scenario through load/run-load.ps1');
}

/** Random RFC 4122 v4 UUID (k6 0.54 has no global crypto.randomUUID). */
export function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

function headers(extra) {
  return Object.assign({ Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' }, extra || {});
}

/** One catalog browse: restaurant list, then a menu (the two cached reads of Feature 2). */
export function browseCatalog() {
  const list = http.get(`${GATEWAY}/api/v1/restaurants?limit=20`, {
    headers: headers(),
    tags: { name: 'GET /restaurants', flow: 'catalog' },
  });
  check(list, { 'restaurants 200': (r) => r.status === 200 });
  const menu = http.get(`${GATEWAY}/api/v1/restaurants/${RESTAURANT_ID}/menu?limit=20`, {
    headers: headers(),
    tags: { name: 'GET /restaurants/{id}/menu', flow: 'catalog' },
  });
  check(menu, { 'menu 200': (r) => r.status === 200 });
  return [list, menu];
}

/**
 * One purchase: quote (Catalog) then order (Orders), each with its own Idempotency-Key.
 * Returns the order response, or null when the quote failed.
 */
export function placeOrder() {
  const quote = http.post(
    `${GATEWAY}/api/v1/quotes`,
    JSON.stringify({ restaurantId: RESTAURANT_ID, items: [{ menuItemId: MENU_ITEM_ID, quantity: 1 }] }),
    { headers: headers({ 'Idempotency-Key': uuid() }), tags: { name: 'POST /quotes', flow: 'order' } }
  );
  if (!check(quote, { 'quote 201': (r) => r.status === 201 })) {
    return null;
  }
  const order = http.post(
    `${GATEWAY}/api/v1/orders`,
    JSON.stringify({
      quoteToken: quote.json('quoteToken'),
      deliveryAddress: { line1: '123 Load Test Street', city: 'Medellin' },
      paymentMethodToken: 'tok_ok',
    }),
    {
      headers: headers({ 'Idempotency-Key': uuid(), 'X-Correlation-ID': uuid() }),
      tags: { name: 'POST /orders', flow: 'order' },
    }
  );
  check(order, { 'order 202': (r) => r.status === 202 });
  return order;
}

const TREND_STATS = ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'];
export const SUMMARY_TREND_STATS = TREND_STATS;

/**
 * Thresholds per request name: the latency target (p95, ms) of the scenario and less than 1% failed requests.
 * Declaring them also makes k6 report the per-request sub-metrics used by the summary.
 */
export function thresholdsFor(targets) {
  const thresholds = {};
  for (const [name, p95] of Object.entries(targets)) {
    thresholds[`http_req_duration{name:${name}}`] = p95 ? [`p(95)<${p95}`] : ['max>=0'];
    thresholds[`http_req_failed{name:${name}}`] = ['rate<0.01'];
  }
  return thresholds;
}

const ms = (v) => (v === undefined ? '-' : v.toFixed(1));

/** Markdown + JSON result of a run, written to /results (load/results on the host) and echoed to stdout. */
export function summarize(data, scenario, description, targets) {
  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const lines = [
    `# ${scenario}`,
    '',
    description,
    '',
    `- Run (UTC): ${new Date().toISOString()}`,
    `- Iterations: ${data.metrics.iterations ? data.metrics.iterations.values.count : 0}`,
    `- Dropped iterations: ${data.metrics.dropped_iterations ? data.metrics.dropped_iterations.values.count : 0}`,
    '',
    '| Request | Count | p50 (ms) | p95 (ms) | p99 (ms) | Max (ms) | Error rate | Target p95 | Result |',
    '| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |',
  ];
  let passed = true;
  for (const [name, p95Target] of Object.entries(targets)) {
    const duration = data.metrics[`http_req_duration{name:${name}}`];
    const failed = data.metrics[`http_req_failed{name:${name}}`];
    if (!duration) continue;
    const v = duration.values;
    const errorRate = failed ? failed.values.rate : 0;
    const count = failed ? failed.values.passes + failed.values.fails : 0;
    const ok =
      count > 0 &&
      Object.values(duration.thresholds || {}).every((t) => t.ok) &&
      Object.values((failed && failed.thresholds) || {}).every((t) => t.ok);
    passed = passed && ok;
    lines.push(
      `| ${name} | ${count} | ${ms(v.med)} | ${ms(v['p(95)'])} | ` +
        `${ms(v['p(99)'])} | ${ms(v.max)} | ${(errorRate * 100).toFixed(2)}% | ` +
        `${p95Target ? `≤ ${p95Target} ms, < 1% errors` : 'informative'} | ${ok ? 'PASS' : 'FAIL'} |`
    );
  }
  lines.push('', `**Overall: ${passed ? 'PASS' : 'FAIL'}**`, '');
  const markdown = lines.join('\n');
  return {
    stdout: `\n${markdown}\n`,
    [`/results/${stamp}-${scenario}.md`]: markdown,
    [`/results/${stamp}-${scenario}.json`]: JSON.stringify(data, null, 2),
  };
}
