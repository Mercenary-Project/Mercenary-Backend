import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.2/index.js";

// ─── 커스텀 메트릭 ────────────────────────────────────────────
// Miss/Hit 각각 Trend를 분리 → 최종 summary에서 p95 비교 가능
const matchListHitDuration    = new Trend('match_list_hit_duration',    true);
const matchListMissDuration   = new Trend('match_list_miss_duration',   true);
const matchDetailHitDuration  = new Trend('match_detail_hit_duration',  true);
const matchDetailMissDuration = new Trend('match_detail_miss_duration', true);
const errorRate               = new Rate('error_rate');

// ─── 설정 ────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    // Phase 1: 캐시 Miss 측정 (캐시에 없는 파라미터 사용)
    cache_miss: {
      executor: 'constant-vus',
      vus: 50,
      duration: '30s',
      startTime: '0s',
      exec: 'runCacheMiss',
      gracefulStop: '5s',
    },
    // Phase 2: 캐시 Hit 측정 (워밍업된 파라미터 사용)
    cache_hit: {
      executor: 'ramping-vus',
      startTime: '40s',  // Miss 시나리오 종료 후 시작
      stages: [
        { duration: '10s', target: 50  },
        { duration: '1m',  target: 100 },
        { duration: '10s', target: 0   },
      ],
      exec: 'runCacheHit',
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    // Hit은 Miss보다 훨씬 빨라야 함 (Redis vs DB)
    'match_list_miss_duration':   ['p(95)<800'],   // DB 직접 조회
    'match_list_hit_duration':    ['p(95)<80'],    // Redis 캐시 Hit
    'match_detail_miss_duration': ['p(95)<500'],
    'match_detail_hit_duration':  ['p(95)<80'],
    'error_rate':                 ['rate<0.01'],
  },
};

// ─── Setup: 테스트 데이터 준비 + 캐시 워밍업 ──────────────────
export function setup() {
  console.log('========================================');
  console.log('  캐시 효과 비교 테스트 (Miss vs Hit)');
  console.log('  Phase 1 (0-30s):  캐시 Miss 측정');
  console.log('  Phase 2 (40s-2m): 캐시 Hit 측정');
  console.log('========================================');

  // 1. 생성자 로그인
  const creatorRes = http.post(
    `${BASE_URL}/api/auth/dev-login`,
    JSON.stringify({ kakaoId: 99999, nickname: '캐시-생성자' }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (creatorRes.status !== 200) {
    console.error(`생성자 로그인 실패: ${creatorRes.body}`);
    return null;
  }
  const creatorToken = creatorRes.json('data.accessToken');

  // 2. 테스트 매치 생성 (600개: 앞 100개=Hit용, 뒤 500개=Miss용)
  const matchIds = [];
  console.log('🏗️ 600개의 매치 데이터 생성 중...');
  for (let i = 0; i < 600; i++) {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const matchDate = tomorrow.toISOString().substring(0, 16);

    const res = http.post(
      `${BASE_URL}/api/matches`,
      JSON.stringify({
        title: `캐시 테스트 매치 ${i + 1}`,
        content: `캐싱 효과 측정용 매치입니다.`,
        placeName: `테스트 구장 ${i + 1}`,
        district: '강남구',
        matchDate: matchDate,
        maxPlayerCount: 22,
        currentPlayerCount: 1,
        latitude: 37.5172 + i * 0.0001,
        longitude: 127.0473 + i * 0.0001,
        fullAddress: `서울특별시 강남구 테스트로 ${i + 1}`,
      }),
      { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${creatorToken}` } }
    );

    if (res.status === 201) matchIds.push(res.json('data'));
  }
  console.log(`✅ ${matchIds.length}개 매치 생성 완료`);

  // 3. VU용 유저 토큰 발급
  const tokens = [];
  for (let i = 0; i < 150; i++) {
    const res = http.post(
      `${BASE_URL}/api/auth/dev-login`,
      JSON.stringify({ kakaoId: 70000 + i, nickname: `캐시-유저-${i}` }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    if (res.status === 200) tokens.push(res.json('data.accessToken'));
  }
  console.log(`✅ ${tokens.length}명 유저 토큰 발급 완료`);

  // 4. 캐시 워밍업 — Hit 시나리오에서 사용할 파라미터만 미리 캐싱
  //    목록: page=0&size=20  |  상세: matchIds[0..99]
  const warmupHeaders = {
    'Authorization': `Bearer ${tokens[0]}`,
    'Content-Type': 'application/json',
  };

  console.log('🔥 캐시 워밍업 시작 (Hit 시나리오용)...');
  http.get(`${BASE_URL}/api/matches?page=0&size=20`, { headers: warmupHeaders });

  const warmupCount = Math.min(100, matchIds.length);
  for (let j = 0; j < warmupCount; j++) {
    http.get(`${BASE_URL}/api/matches/${matchIds[j]}`, { headers: warmupHeaders });
  }
  console.log(`✅ 캐시 워밍업 완료 (목록 1회 + 상세 ${warmupCount}개)`);
  console.log(`ℹ️  Miss용 matchId 범위: index 100 ~ ${matchIds.length - 1}`);

  return { matchIds, tokens };
}

// ─── Phase 1: 캐시 Miss 시나리오 ─────────────────────────────
// size=11~19 → 워밍업된 size=20과 다른 캐시 키 → DB 직접 조회
// matchIds[100..] → 워밍업 범위 밖 → matchDetail 캐시 없음
export function runCacheMiss(data) {
  if (!data || !data.tokens.length) return;

  const { matchIds, tokens } = data;
  const token = tokens[__VU % tokens.length];
  const headers = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  };

  // 목록: size를 11~19로 변화시켜 캐시 없는 파라미터 사용
  group('[Miss] 매치 목록 조회', () => {
    const missSize = 11 + (__VU % 9);  // 11, 12, ..., 19
    const res = http.get(
      `${BASE_URL}/api/matches?page=0&size=${missSize}`,
      { headers, tags: { name: '매치_목록_Miss' } }
    );
    matchListMissDuration.add(res.timings.waiting);
    const ok = check(res, { '200 OK': (r) => r.status === 200 });
    errorRate.add(!ok);
  });

  sleep(0.1);

  // 상세: 워밍업 범위 밖(index 100+) matchId 사용
  group('[Miss] 매치 상세 조회', () => {
    const missOffset = 100 + (__VU % (matchIds.length - 100));
    const matchId = matchIds[missOffset];
    if (!matchId) return;
    const res = http.get(
      `${BASE_URL}/api/matches/${matchId}`,
      { headers, tags: { name: '매치_상세_Miss' } }
    );
    matchDetailMissDuration.add(res.timings.waiting);
    const ok = check(res, { '200 OK': (r) => r.status === 200 });
    errorRate.add(!ok);
  });

  sleep(0.1);
}

// ─── Phase 2: 캐시 Hit 시나리오 ──────────────────────────────
// size=20, page=0 → 워밍업으로 캐싱된 키 → Redis에서 즉시 반환
// matchIds[0..99] → 워밍업 완료 → matchDetail 캐시 Hit
export function runCacheHit(data) {
  if (!data || !data.tokens.length) return;

  const { matchIds, tokens } = data;
  const token = tokens[__VU % tokens.length];
  const headers = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  };

  group('[Hit] 매치 목록 조회', () => {
    const res = http.get(
      `${BASE_URL}/api/matches?page=0&size=20`,
      { headers, tags: { name: '매치_목록_Hit' } }
    );
    matchListHitDuration.add(res.timings.waiting);
    const ok = check(res, { '200 OK': (r) => r.status === 200 });
    errorRate.add(!ok);
  });

  sleep(0.1);

  group('[Hit] 매치 상세 조회', () => {
    const matchId = matchIds[__VU % 100];  // 워밍업된 0~99 인덱스
    if (!matchId) return;
    const res = http.get(
      `${BASE_URL}/api/matches/${matchId}`,
      { headers, tags: { name: '매치_상세_Hit' } }
    );
    matchDetailHitDuration.add(res.timings.waiting);
    const ok = check(res, { '200 OK': (r) => r.status === 200 });
    errorRate.add(!ok);
  });

  sleep(0.1);
}

// ─── 결과 요약 ──────────────────────────────────────────────
export function handleSummary(data) {
  // 콘솔에 Miss vs Hit 비교 출력
  const metrics = data.metrics;
  const safeP95 = (name) => {
    const m = metrics[name];
    if (!m || !m.values) return 'N/A';
    return (m.values['p(95)'] || m.values['p95'] || 0).toFixed(1) + 'ms';
  };

  console.log('\n╔══════════════════════════════════════════════╗');
  console.log('║        캐시 효과 비교 결과 (TTFB p95)        ║');
  console.log('╠══════════════════════════════════════════════╣');
  console.log(`║ 목록 조회  Miss (DB)  : ${safeP95('match_list_miss_duration').padStart(10)} ║`);
  console.log(`║ 목록 조회  Hit (Redis): ${safeP95('match_list_hit_duration').padStart(10)} ║`);
  console.log('╠══════════════════════════════════════════════╣');
  console.log(`║ 상세 조회  Miss (DB)  : ${safeP95('match_detail_miss_duration').padStart(10)} ║`);
  console.log(`║ 상세 조회  Hit (Redis): ${safeP95('match_detail_hit_duration').padStart(10)} ║`);
  console.log('╚══════════════════════════════════════════════╝\n');

  return {
    'summary-cache-read.html': htmlReport(data),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}
