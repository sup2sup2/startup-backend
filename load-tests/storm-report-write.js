import http from "k6/http";
import encoding from "k6/encoding";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL;
const TOKEN = __ENV.AUTH_TOKEN;
const CONFIRM_WRITES = __ENV.CONFIRM_WRITES;

if (!BASE_URL || !TOKEN) {
  throw new Error("BASE_URL and AUTH_TOKEN are required.");
}
if (CONFIRM_WRITES !== BASE_URL) {
  throw new Error("Set CONFIRM_WRITES to the same BASE_URL to acknowledge report creation.");
}

const tinyPng = encoding.b64decode(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
  "std",
);
const failures = new Rate("write_failures");

export const options = {
  scenarios: {
    report_writes: {
      executor: "ramping-arrival-rate",
      startRate: 1,
      timeUnit: "1s",
      preAllocatedVUs: 10,
      maxVUs: 40,
      stages: [
        { target: 2, duration: "20s" },
        { target: 5, duration: "30s" },
        { target: 10, duration: "20s" },
        { target: 0, duration: "10s" },
      ],
    },
  },
  thresholds: {
    write_failures: ["rate<0.01"],
    http_req_duration: ["p(95)<2000"],
  },
};

export default function () {
  const payload = {
    latitude: "37.5665",
    longitude: "126.9780",
    description: `k6 storm write test vu=${__VU}`,
    image: http.file(tinyPng, "load-test.png", "image/png"),
  };
  const response = http.post(`${BASE_URL}/api/reports`, payload, {
    headers: { Authorization: `Bearer ${TOKEN}` },
    tags: { endpoint: "report-write" },
  });
  const ok = check(response, {
    "report created": (res) => res.status === 200,
  });
  failures.add(!ok);
  sleep(Math.random() * 0.2);
}
