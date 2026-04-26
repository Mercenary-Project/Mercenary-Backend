import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.2/index.js";

// ─── 커스텀 메트릭 (ASCII 이름 사용 필수) ──────────────────────
const applySuccess = new Counter('apply_success');   
const applyFail    = new Counter('apply_fail');   
const applyDuration = new Trend('apply_duration'); 

// ─── 설정 ────────────────────────────────────────────────────
const BASE_URL       = __ENV.BASE_URL || 'http://localhost:8080';
const TOTAL_USERS    = 100;  
const MAX_PLAYERS    = 10;   
const INITIAL_COUNT  = 1;    
const EXPECTED_SUCCESS = MAX_PLAYERS - INITIAL_COUNT; 

export const options = {
  scenarios: {
    concurrency: {
      executor: 'per-vu-iterations',
      vus: TOTAL_USERS,
      iterations: 1,          
      maxDuration: '60s',
    },
  },
  thresholds: {
    'apply_success': [`count==${EXPECTED_SUCCESS}`],  
    'apply_fail':    [`count==${TOTAL_USERS - EXPECTED_SUCCESS}`], 
  },
};

// ─── Setup: 유저 생성 + JWT 발급 + 매치 생성 ──────────────────
export function setup() {
  console.log('========================================');
  console.log('  동시성 제어 테스트 시작 (Concurrency Test)');
  console.log('========================================');
  console.log(`총 유저: ${TOTAL_USERS}명 | 매치 정원: ${MAX_PLAYERS}명 | 남은 자리: ${EXPECTED_SUCCESS}`);

  // 1. 매치 생성자 dev-login
  const creatorRes = http.post(
    `${BASE_URL}/api/auth/dev-login`,
    JSON.stringify({ kakaoId: 9999, nickname: '매치-생성자' }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(creatorRes, { '생성자 로그인 성공': (r) => r.status === 200 });
  if (creatorRes.status !== 200) {
    console.error(`생성자 로그인 실패: ${creatorRes.status} - ${creatorRes.body}`);
    return null;
  }

  const creatorToken = creatorRes.json('data.accessToken');
  console.log(`✅ 매치 생성자 로그인 완료 (회원ID: ${creatorRes.json('data.memberId')})`);

  // 2. 매치 생성
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const matchDate = tomorrow.toISOString().substring(0, 16);

  const matchRes = http.post(
    `${BASE_URL}/api/matches`,
    JSON.stringify({
      title: 'k6 동시성 테스트 매치',
      content: '동시성 제어 성능 테스트를 위한 매치입니다.',
      placeName: '테스트 구장',
      district: '강남구',
      matchDate: matchDate,
      maxPlayerCount: MAX_PLAYERS,
      currentPlayerCount: INITIAL_COUNT,
      latitude: 37.5172,
      longitude: 127.0473,
      fullAddress: '서울특별시 강남구 테스트로 1',
    }),
    { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${creatorToken}` } }
  );

  check(matchRes, { '매치 생성 성공': (r) => r.status === 201 });
  if (matchRes.status !== 201) {
    console.error(`매치 생성 실패: ${matchRes.status} - ${matchRes.body}`);
    return null;
  }

  const matchId = matchRes.json('data');
  console.log(`✅ 매치 생성 완료 (매치ID: ${matchId})`);

  // 3. 신청자 100명 dev-login → JWT 발급
  const tokens = [];
  for (let i = 0; i < TOTAL_USERS; i++) {
    const res = http.post(
      `${BASE_URL}/api/auth/dev-login`,
      JSON.stringify({ kakaoId: 10000 + i, nickname: `테스트-유저-${i}` }),
      { headers: { 'Content-Type': 'application/json' } }
    );

    if (res.status === 200) {
      tokens.push(res.json('data.accessToken'));
    } else {
      console.error(`유저 ${i} 로그인 실패: ${res.status}`);
      tokens.push(null);
    }
  }

  console.log(`✅ 유저 ${tokens.filter(t => t !== null).length}명 로그인 완료`);

  return { matchId, tokens, creatorToken };
}

// ─── 메인 테스트: 각 VU가 동시에 매치 신청 ─────────────────────
export default function (data) {
  if (!data) return;

  const { matchId, tokens } = data;
  const vuIndex = __VU - 1;  
  const token = tokens[vuIndex];

  if (!token) {
    applyFail.add(1);
    return;
  }

  // 동시에 매치 신청!
  const res = http.post(
    `${BASE_URL}/api/matches/${matchId}/apply`,
    null,
    {
      headers: { 'Authorization': `Bearer ${token}` },
      tags: { name: '매치_신청' },
    }
  );

  applyDuration.add(res.timings.duration);

  if (res.status === 200) {
    applySuccess.add(1);
  } else {
    applyFail.add(1);
  }
}

// ─── Teardown: 결과 검증 ─────────────────────────────────────
export function teardown(data) {
  if (!data) return;

  const { matchId, creatorToken } = data;

  const matchRes = http.get(
    `${BASE_URL}/api/matches/${matchId}`,
    { headers: { 'Authorization': `Bearer ${creatorToken}` } }
  );

  if (matchRes.status === 200) {
    const matchData = matchRes.json('data');
    const currentCount = matchData.currentPlayerCount;
    const maxCount = matchData.maxPlayerCount;

    console.log('\n========================================');
    console.log('  동시성 테스트 결과 요약');
    console.log('========================================');
    console.log(`매치 정원:    ${maxCount}명`);
    console.log(`현재 인원:    ${currentCount}명`);
    console.log(`기대 인원:    ${MAX_PLAYERS}명`);
    console.log(`정합성 검증:  ${currentCount === MAX_PLAYERS ? '✅ 통과 (PASS)' : '❌ 실패 (FAIL)'}`);
    console.log('========================================\n');
  }
}

// ─── HTML 리포트 생성 ────────────────────────────────────────
export function handleSummary(data) {
  return {
    "concurrency_summary.html": htmlReport(data, { title: "동시성 테스트 결과 리포트" }),
    stdout: textSummary(data, { indent: " ", enableColors: true }),
  };
}
