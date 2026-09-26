import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const DISTRICTS = ["강남구", "관악구", "동작구", "마포구", "서초구", "영등포구"];

const requestFailures = new Rate("request_failures");
const riskDuration = new Trend("risk_duration", true);

export const options = {
  scenarios: {
    storm_reads: {
      executor: "ramping-arrival-rate",
      startRate: 5,
      timeUnit: "1s",
      preAllocatedVUs: 20,
      maxVUs: 150,
      stages: [
        { target: 5, duration: "30s" },
        { target: 15, duration: "1m" },
        { target: 30, duration: "1m" },
        { target: 50, duration: "30s" },
        { target: 0, duration: "15s" },
      ],
    },
  },
  thresholds: {
    request_failures: ["rate<0.01"],
    http_req_duration: ["p(95)<1000"],
    risk_duration: ["p(95)<800"],
  },
};

export default function () {
  const roll = Math.random();
  let response;

  if (roll < 0.7) {
    const district = DISTRICTS[Math.floor(Math.random() * DISTRICTS.length)];
    response = http.get(`${BASE_URL}/api/risk?district=${encodeURIComponent(district)}`, {
      tags: { endpoint: "risk" },
    });
    riskDuration.add(response.timings.duration);
  } else if (roll < 0.9) {
    response = http.get(`${BASE_URL}/api/reports/page?size=50`, {
      tags: { endpoint: "reports" },
    });
  } else {
    response = http.get(`${BASE_URL}/api/weather/rainfall`, {
      tags: { endpoint: "rainfall" },
    });
  }

  const ok = check(response, {
    "status is 200": (res) => res.status === 200,
    "response is not empty": (res) => Boolean(res.body),
  });
  requestFailures.add(!ok);
  sleep(Math.random() * 0.2);
}
