import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    normal: { executor: 'constant-vus', vus: 5, duration: '1m', exec: 'normalTraffic' },
    abusive: { executor: 'constant-arrival-rate', rate: 250, timeUnit: '1s', duration: '1m', preAllocatedVUs: 50, exec: 'abuseTraffic' }
  },
  thresholds: {
    http_req_duration: ['p(99)<500'],
    http_req_failed: ['rate<0.20']
  }
};

export function normalTraffic() {
  const res = http.get('http://localhost:8080/api/anything', { headers: { 'X-Tenant-Id': 'good-tenant' } });
  check(res, { 'normal traffic not blocked': r => r.status < 429 });
  sleep(0.2);
}

export function abuseTraffic() {
  http.get('http://localhost:8080/auth/login', { headers: { 'X-Tenant-Id': 'abusive-tenant' } });
}
