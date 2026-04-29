import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.2/index.js";

// ─── 커스텀 메트릭 ────────────────────────────────────────────
// TTFB(timings.waiting)로 측정 — 서버 처리시간만, 네트워크 왕복 제외
const geoSearchDuration  = new Trend('geo_search_duration',   true);
const geoSearchErrorRate = new Rate('geo_search_error_rate');

// ─── 설정 ────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 서울 강남구 중심 좌표 (테스트 데이터 기준점)
const CENTER_LAT = 37.5172;
const CENTER_LNG = 127.0473;

export const options = {
  // 10,000개 매치 순차 생성(~5-7분) + 사용자 200명 생성을 고려한 타임아웃
  setupTimeout: '10m',
  scenarios: {
    // ramping-vus: 점진적 부하 → 유지 → 종료
    geo_search: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 50  },  // 0→50 VU 증가
        { duration: '1m',  target: 100 },  // 100 VU 유지
        { duration: '30s', target: 0   },  // 0으로 감소
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    // 로컬 Docker 환경 기준: 100 VU 동시, 1km 반경 ~70건 반환 → 2,000ms 이내
    // (프로덕션 전용 Redis + DB 환경에서는 p95 < 150ms 목표)
    'geo_search_duration':   ['p(95)<2000'],
    // 에러율 1% 미만
    'geo_search_error_rate': ['rate<0.01'],
  },
};

// ─── Setup: 테스트 데이터 준비 ───────────────────────────────
export function setup() {
  console.log('========================================');
  console.log('  Redis GEO 주변 검색 부하 테스트');
  console.log('  강남구 중심 반경 10km 내 매치 10,000개');
  console.log('  VU: 0 → 50 → 100 → 0 (총 2분)');
  console.log('========================================');

  // 1. 생성자 계정 로그인 (kakaoId 88888 — geo 테스트 전용)
  const creatorRes = http.post(
    `${BASE_URL}/api/auth/dev-login`,
    JSON.stringify({ kakaoId: 88888, nickname: 'GEO-생성자' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (creatorRes.status !== 200) {
    console.error(`생성자 로그인 실패: ${creatorRes.body}`);
    return null;
  }
  const creatorToken = creatorRes.json('data.accessToken');
  console.log('✅ 생성자 로그인 완료');

  // 2. 강남 중심 반경 10km 내 랜덤 좌표로 매치 10,000개 생성
  //    매치 생성 시 MatchLocationService.addMatchLocation()이 자동으로 Redis GEO에 저장
  //    위도 ±0.09° ≈ 10km / 경도 ±0.11° ≈ 10km
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const matchDate = tomorrow.toISOString().substring(0, 16);

  let successCount = 0;
  let failCount    = 0;
  const TOTAL = 10000;

  console.log(`⏳ ${TOTAL}개 매치 생성 중... (약 5-7분 소요)`);

  for (let i = 0; i < TOTAL; i++) {
    const lat = CENTER_LAT + (Math.random() - 0.5) * 0.18;  // ±0.09°
    const lng = CENTER_LNG + (Math.random() - 0.5) * 0.22;  // ±0.11°

    const res = http.post(
      `${BASE_URL}/api/matches`,
      JSON.stringify({
        title:       `GEO 테스트 매치 ${i + 1}`,
        content:     'Redis GEO 부하 테스트용 매치입니다.',
        placeName:   `GEO 구장 ${i + 1}`,
        district:    '강남구',
        matchDate:   matchDate,
        latitude:    lat,
        longitude:   lng,
        fullAddress: `서울특별시 강남구 GEO로 ${i + 1}`,
        slots: [{ position: 'ST', required: 20 }],
      }),
      { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${creatorToken}` } }
    );

    if (res.status === 201) {
      successCount++;
    } else {
      failCount++;
      // 연속 실패 50건 이상이면 setup 중단 (서버 장애 가능성)
      if (failCount > 50 && successCount === 0) {
        console.error(`매치 생성 연속 실패 — 서버 응답 이상. 마지막 응답: ${res.body}`);
        return null;
      }
    }

    // 1,000건마다 진행 상황 출력
    if ((i + 1) % 1000 === 0) {
      console.log(`  진행: ${i + 1}/${TOTAL} (성공 ${successCount}, 실패 ${failCount})`);
    }
  }

  console.log(`✅ 매치 생성 완료 — 성공 ${successCount}개, 실패 ${failCount}개`);

  if (successCount === 0) {
    console.error('매치가 하나도 생성되지 않았습니다. 테스트를 중단합니다.');
    return null;
  }

  // 3. VU용 사용자 200명 생성 (kakaoId 85000 – 85199 — geo 테스트 전용)
  const tokens = [];
  for (let i = 0; i < 200; i++) {
    const res = http.post(
      `${BASE_URL}/api/auth/dev-login`,
      JSON.stringify({ kakaoId: 85000 + i, nickname: `GEO-유저-${i}` }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    if (res.status === 200) tokens.push(res.json('data.accessToken'));
  }
  console.log(`✅ VU 토큰 발급 완료 — ${tokens.length}명`);

  if (tokens.length === 0) {
    console.error('VU 토큰이 하나도 발급되지 않았습니다. 테스트를 중단합니다.');
    return null;
  }

  console.log('🚀 부하 테스트 시작');
  return { tokens };
}

// ─── VU 로직: Redis GEO 주변 매치 검색 ──────────────────────
// VU마다 검색 중심점을 ±0.01°(약 1km) 미세 변화
// → 모든 VU가 동일 좌표를 쓰면 쿼리가 집중되어 실 부하를 과소 측정할 수 있음
export default function (data) {
  if (!data || !data.tokens.length) return;

  const token = data.tokens[__VU % data.tokens.length];
  const headers = {
    'Authorization': `Bearer ${token}`,
    'Content-Type':  'application/json',
  };

  group('[GEO] 주변 매치 검색', () => {
    const lat = CENTER_LAT + (Math.random() - 0.5) * 0.02;  // ±0.01° ≈ 1km 변화
    const lng = CENTER_LNG + (Math.random() - 0.5) * 0.02;

    const res = http.get(
      `${BASE_URL}/api/matches/nearby?latitude=${lat.toFixed(6)}&longitude=${lng.toFixed(6)}&distance=1`,
      { headers, tags: { name: '주변_매치_검색' } }
    );

    // TTFB(서버 처리시간)만 기록
    geoSearchDuration.add(res.timings.waiting);

    const ok = check(res, {
      '200 OK':    (r) => r.status === 200,
      '결과 배열': (r) => Array.isArray(r.json('data')),
    });
    geoSearchErrorRate.add(!ok);
  });

  sleep(0.2);
}

// ─── 결과 요약 ──────────────────────────────────────────────
export function handleSummary(data) {
  const metrics = data.metrics;
  const safeP95 = (name) => {
    const m = metrics[name];
    if (!m || !m.values) return 'N/A';
    return (m.values['p(95)'] || m.values['p95'] || 0).toFixed(1) + 'ms';
  };
  const safeRate = (name) => {
    const m = metrics[name];
    if (!m || !m.values) return 'N/A';
    return (m.values.rate * 100).toFixed(2) + '%';
  };

  console.log('\n╔══════════════════════════════════════════════╗');
  console.log('║    Redis GEO 주변 검색 부하 테스트 결과      ║');
  console.log('╠══════════════════════════════════════════════╣');
  console.log(`║ GEO 검색  응답시간 p95: ${safeP95('geo_search_duration').padStart(13)} ║`);
  console.log(`║ GEO 검색  에러율     : ${safeRate('geo_search_error_rate').padStart(13)} ║`);
  console.log('╚══════════════════════════════════════════════╝\n');

  return {
    'summary-geo-search.html': htmlReport(data),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}
