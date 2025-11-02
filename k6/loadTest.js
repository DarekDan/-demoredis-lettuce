import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// --- Configuration ---
const BASE_URL = 'http://localhost:8080'; // Target your Spring Boot app
const MAX_ITEM_ID = 1001; // 1 default item + 1000 random items

// --- Custom Metrics ---
const readRequestDuration = new Trend('read_request_duration');
const writeRequestDuration = new Trend('write_request_duration');
const readFailureRate = new Rate('read_failure_rate');
const writeFailureRate = new Rate('write_failure_rate');
const cacheHitRate = new Rate('cache_hit_rate');

// --- Test Options ---
export const options = {
    scenarios: {
        // Scenario 1: Read-heavy workload
        // 90% of traffic. VUs will hit the GET endpoint, testing cache performance.
        read_scenario: {
            executor: 'constant-arrival-rate',
            rate: 900, // 90 iterations per second
            timeUnit: '1s',
            duration: '1m', // Run for 1 minute
            preAllocatedVUs: 100,
            maxVUs: 500,
            exec: 'readTest', // Function to execute
        },
        // Scenario 2: Write-heavy workload
        // 10% of traffic. VUs will hit the PUT endpoint, testing cache invalidation.
        write_scenario: {
            executor: 'constant-arrival-rate',
            rate: 100, // 10 iterations per second
            timeUnit: '1s',
            duration: '1m', // Run for 1 minute
            preAllocatedVUs: 50,
            maxVUs: 200,
            exec: 'writeTest', // Function to execute
        },
    },
    thresholds: {
        // 95% of all requests must complete within 200ms
        'http_req_duration': ['p(95)<200'],
        // 95% of read requests must be fast (hitting cache)
        'read_request_duration{scenario:read_scenario}': ['p(95)<50'],
        // 95% of write requests (DB + cache evict)
        'write_request_duration{scenario:write_scenario}': ['p(95)<150'],
        // Failure rates must be less than 1%
        'read_failure_rate{scenario:read_scenario}': ['rate<0.01'],
        'write_failure_rate{scenario:write_scenario}': ['rate<0.01'],
        // We expect cache hits to be high (e.g., > 80%)
        // This depends on the 15s TTL, so it will fluctuate.
        // In a 1m test, it should be high after the first 15s.
        'cache_hit_rate{scenario:read_scenario}': ['rate>0.5'], // Expecting at least 50% cache hits
    },
};

// --- Scenario 1: Read Test (GET) ---
export function readTest() {
    // Get a random item ID for each iteration
    const ITEM_ID_TO_TEST = Math.floor(Math.random() * MAX_ITEM_ID) + 1;

    const res = http.get(`${BASE_URL}/items/${ITEM_ID_TO_TEST}`);

    const isSuccess = check(res, {
        'GET status is 200': (r) => r.status === 200,
    });

    // Add metrics
    readRequestDuration.add(res.timings.duration, { scenario: 'read_scenario' });
    readFailureRate.add(!isSuccess, { scenario: 'read_scenario' });

    // Check if it was a cache hit
    if (isSuccess && res.json('message') === 'Retrieved from cache') {
        cacheHitRate.add(true, { scenario: 'read_scenario' });
    } else {
        cacheHitRate.add(false, { scenario: 'read_scenario' });
    }

    sleep(0.01); // Wait 10ms (was 500ms, reduced for higher load)
}

// --- Scenario 2: Write Test (PUT) ---
export function writeTest() {
    // Get a random item ID for each iteration
    const ITEM_ID_TO_TEST = Math.floor(Math.random() * MAX_ITEM_ID) + 1;

    const url = `${BASE_URL}/items/${ITEM_ID_TO_TEST}`;

    // Generate a random name to update
    const randomName = `Item_Updated_by_k6_${Math.random()}`;
    const payload = JSON.stringify({
        name: randomName,
        description: 'Load test update',
    });
    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const res = http.put(url, payload, params);

    const isSuccess = check(res, {
        'PUT status is 200': (r) => r.status === 200,
        'PUT response has new name': (r) => r.json('name') === randomName,
    });

    // Add metrics
    writeRequestDuration.add(res.timings.duration, { scenario: 'write_scenario' });
    writeFailureRate.add(!isSuccess, { scenario: 'write_scenario' });

    sleep(0.05); // Wait 50ms (was 1s, reduced for higher load)
}

