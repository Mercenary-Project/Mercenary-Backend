import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.2/index.js";

// ─── 커스텀 메트릭 (ASCII 이름 사용 필수) ──────────────────────
const matchListDuration   = new Trend('match_list_duration', true);
const matchApplyDuration  = new Trend('match_apply_duration', true);
const errorRate           = new Rate('error_rate');

// ─── 설정 ────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VU_COUNT = 200;

export const options = {
  scenarios: {
    load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50  },  // 워밍업: 0 → 50 VU
        { duration: '30s', target: VU_COUNT },  // 램프업: 50 → 200 VU
        { duration: '1m',  target: VU_COUNT },  // 유지: 200 VU
        { duration: '20s', target: 0 },    // 종료: 200 → 0 VU
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    'http_req_duration':       ['p(95)<3000'],   // 전체 응답 3초 이내
    'http_req_duration{name:매치_목록_조회}':  ['p(95)<1000'],
    'http_req_duration{name:매치_신청}': ['p(95)<2000'],
    'error_rate':              ['rate<0.15'],     // 에러율 15% 미만
  },
};

// ─── Setup: 테스트 데이터 준비 ────────────────────────────────
export function setup() {
  console.log('========================================');
  console.log('  부하 테스트 시작 (Load Test)');
  console.log(`  최대 VU: ${VU_COUNT} | 총 시간: ~2분`);
  console.log('========================================');

  // 1. 매치 생성자 로그인
  const creatorRes = http.post(
    `${BASE_URL}/api/auth/dev-login`,
    JSON.stringify({ kakaoId: 50000, nickname: '부하-생성자' }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (creatorRes.status !== 200) {
    console.error(`생성자 로그인 실패: ${creatorRes.body}`);
    return null;
  }
  const creatorToken = creatorRes.json('data.accessToken');

  // 2. 부하 테스트용 매치 여러 개 생성 (각각 정원 넉넉하게)
  const matchIds = [];
  for (let i = 0; i < 5; i++) {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1 + i);
    const matchDate = tomorrow.toISOString().substring(0, 16);

    const res = http.post(
      `${BASE_URL}/api/matches`,
      JSON.stringify({
        title: `부하 테스트 매치 ${i + 1}`,
        content: `부하 테스트를 위한 매치 ${i + 1}번입니다.`,
        placeName: `테스트 구장 ${i + 1}`,
        district: '강남구',
        matchDate: matchDate,
        latitude: 37.5172 + i * 0.01,
        longitude: 127.0473 + i * 0.01,
        fullAddress: `서울특별시 강남구 테스트로 ${i + 1}`,
        slots: [
          { position: 'GK',  required: 2 },
          { position: 'CB',  required: 4 },
          { position: 'CM',  required: 6 },
          { position: 'ST',  required: 4 },
        ],
      }),
      { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${creatorToken}` } }
    );

    if (res.status === 201) {
      matchIds.push(res.json('data'));
    } else {
      console.error(`매치 생성 실패: ${res.status} - ${res.body}`);
    }
  }
  console.log(`✅ ${matchIds.length}개 매치 생성 완료`);

  // 3. VU용 유저 토큰 발급 (넉넉하게 200명)
  const tokens = [];
  for (let i = 0; i < 200; i++) {
    const res = http.post(
      `${BASE_URL}/api/auth/dev-login`,
      JSON.stringify({ kakaoId: 60000 + i, nickname: `부하-유저-${i}` }),
      { headers: { 'Content-Type': 'application/json' } }
    );

    if (res.status === 200) {
      tokens.push(res.json('data.accessToken'));
    }
  }
  console.log(`✅ ${tokens.length}명 유저 토큰 발급 완료`);

  return { matchIds, tokens, creatorToken };
}

// ─── 메인 테스트: 혼합 시나리오 ──────────────────────────────
export default function (data) {
  if (!data || !data.tokens.length) return;

  const { matchIds, tokens } = data;
  const token = tokens[__VU % tokens.length];
  const headers = { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' };

  // ── 시나리오 1: 매치 목록 조회 ─────────────────────────────
  group('매치 목록 조회', () => {
    const res = http.get(`${BASE_URL}/api/matches?page=0&size=20`, {
      headers,
      tags: { name: '매치_목록_조회' },
    });

    const success = check(res, { '조회 성공 (200)': (r) => r.status === 200 });
    errorRate.add(!success);
    matchListDuration.add(res.timings.waiting);
  });

  sleep(0.5);

  // ── 시나리오 2: 매치 상세 조회 ─────────────────────────────
  group('매치 상세 조회', () => {
    const matchId = matchIds[Math.floor(Math.random() * matchIds.length)];
    if (!matchId) return;
    const res = http.get(`${BASE_URL}/api/matches/${matchId}`, {
      headers,
      tags: { name: '매치_상세_조회' },
    });

    const success = check(res, { '상세 성공 (200)': (r) => r.status === 200 });
    errorRate.add(!success);
  });

  sleep(0.5);

  // ── 시나리오 3: 매치 신청 (동시성 제어 대상) ────────────────
  group('매치 신청', () => {
    const matchId = matchIds[Math.floor(Math.random() * matchIds.length)];
    if (!matchId) return;
    const res = http.post(`${BASE_URL}/api/matches/${matchId}/apply`,
      JSON.stringify({ position: 'ST' }),
      {
        headers,
        tags: { name: '매치_신청' },
      }
    );

    // 200(성공) 또는 409(중복/마감) 모두 정상 응답
    const success = check(res, {
      '신청 응답 정상 (200 or 409)': (r) => r.status === 200 || r.status === 409,
    });
    errorRate.add(!success);
    matchApplyDuration.add(res.timings.waiting);
  });

  sleep(1);
}

// ─── Teardown: 결과 요약 ─────────────────────────────────────
export function teardown(data) {
  console.log('\n========================================');
  console.log('  부하 테스트 완료');
  console.log('========================================');
  console.log('결과는 생성된 summary.html 파일을 확인하세요.');
  console.log('========================================\n');
}

// ─── HTML 리포트 생성 ────────────────────────────────────────
export function handleSummary(data) {
  return {
    "summary.html": htmlReport(data),
    stdout: textSummary(data, { indent: " ", enableColors: true }),
  };
}
