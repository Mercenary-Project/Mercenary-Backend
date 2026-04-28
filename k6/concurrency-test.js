import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.2/index.js";

// ─── 커스텀 메트릭 ────────────────────────────────────────────
// 메트릭명은 Grafana 대시보드 쿼리와 일치시켜 유지
const applySuccess  = new Counter('apply_success');   // 승인 성공 수
const applyFail     = new Counter('apply_fail');      // 승인 실패 수 (슬롯 초과 409)
const applyDuration = new Trend('apply_duration');    // 승인 응답시간

// ─── 설정 ────────────────────────────────────────────────────
const BASE_URL       = __ENV.BASE_URL || 'http://localhost:8080';
const TOTAL_USERS    = 100;  // 신청자 수
const SLOT_REQUIRED  = 9;    // ST 포지션 모집 인원 (매치 정원 - 1)
const EXPECTED_APPROVED = SLOT_REQUIRED;                      // 9
const EXPECTED_REJECTED = TOTAL_USERS - EXPECTED_APPROVED;    // 91

// ─── 테스트 흐름 ─────────────────────────────────────────────
// 1. Setup:  100명 사전 신청 완료 → applicationId 목록 수집
// 2. 본테스트: 매치 주인이 100개 신청을 동시에 APPROVE 요청
// 3. Teardown: 분산 락이 정확히 9건만 승인했는지 DB 정합성 검증
// ─────────────────────────────────────────────────────────────

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
    'apply_success': [`count==${EXPECTED_APPROVED}`],
    'apply_fail':    [`count==${EXPECTED_REJECTED}`],
  },
};

// ─── Setup: 사전 신청 + applicationId 목록 수집 ───────────────
export function setup() {
  console.log('========================================');
  console.log('  동시성 제어 테스트 시작 (Concurrency Test)');
  console.log('  방식: 100명 사전 신청 → 동시 승인 → 9건만 통과');
  console.log('========================================');
  console.log(`ST 포지션 정원: ${SLOT_REQUIRED}명 | 신청자: ${TOTAL_USERS}명`);

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

  // 2. 매치 생성 (ST 포지션 9자리)
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const matchDate = tomorrow.toISOString().substring(0, 16);

  const matchRes = http.post(
    `${BASE_URL}/api/matches`,
    JSON.stringify({
      title: 'k6 동시성 테스트 매치',
      content: '분산 락 기반 동시성 제어 성능 테스트용 매치입니다.',
      placeName: '테스트 구장',
      district: '강남구',
      matchDate: matchDate,
      latitude: 37.5172,
      longitude: 127.0473,
      fullAddress: '서울특별시 강남구 테스트로 1',
      slots: [
        { position: 'ST', required: SLOT_REQUIRED },
      ],
    }),
    { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${creatorToken}` } }
  );

  check(matchRes, { '매치 생성 성공': (r) => r.status === 201 });
  if (matchRes.status !== 201) {
    console.error(`매치 생성 실패: ${matchRes.status} - ${matchRes.body}`);
    return null;
  }

  const matchId = matchRes.json('data');
  console.log(`✅ 매치 생성 완료 (매치ID: ${matchId}, ST ${SLOT_REQUIRED}자리)`);

  // 3. 신청자 100명 dev-login → JWT 발급
  const tokens = [];
  for (let i = 0; i < TOTAL_USERS; i++) {
    const res = http.post(
      `${BASE_URL}/api/auth/dev-login`,
      JSON.stringify({ kakaoId: 10000 + i, nickname: `테스트-유저-${i}` }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    tokens.push(res.status === 200 ? res.json('data.accessToken') : null);
  }
  console.log(`✅ 유저 ${tokens.filter(t => t !== null).length}명 로그인 완료`);

  // 4. 100명 전원 ST 포지션 사전 신청
  let preApplyCount = 0;
  for (let i = 0; i < TOTAL_USERS; i++) {
    if (!tokens[i]) continue;
    const res = http.post(
      `${BASE_URL}/api/matches/${matchId}/apply`,
      JSON.stringify({ position: 'ST' }),
      { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokens[i]}` } }
    );
    if (res.status === 200) preApplyCount++;
  }
  console.log(`✅ 사전 신청 완료: ${preApplyCount}명 / ${TOTAL_USERS}명`);

  // 5. 신청 목록 조회 → applicationId 수집
  const appsRes = http.get(
    `${BASE_URL}/api/matches/${matchId}/applications`,
    { headers: { 'Authorization': `Bearer ${creatorToken}` } }
  );

  if (appsRes.status !== 200) {
    console.error(`신청 목록 조회 실패: ${appsRes.status} - ${appsRes.body}`);
    return null;
  }

  const applicationIds = appsRes.json('data').map(app => app.applicationId);
  console.log(`✅ 신청 ID 수집 완료: ${applicationIds.length}건 (동시 승인 준비)`);

  return { matchId, applicationIds, creatorToken };
}

// ─── 메인 테스트: 100 VU가 동시에 동일 매치 승인 요청 ──────────
// 분산 락(Redisson)이 slot.filled 증가를 직렬화 → 정확히 9건만 통과
export default function (data) {
  if (!data || !data.applicationIds) return;

  const { matchId, applicationIds, creatorToken } = data;
  const vuIndex = __VU - 1;
  const appId = applicationIds[vuIndex];

  if (!appId) {
    applyFail.add(1);
    return;
  }

  const res = http.patch(
    `${BASE_URL}/api/matches/${matchId}/applications/${appId}`,
    JSON.stringify({ status: 'APPROVED' }),
    {
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${creatorToken}` },
      tags: { name: '승인_요청' },
    }
  );

  applyDuration.add(res.timings.duration);

  if (res.status === 200) {
    applySuccess.add(1);
  } else {
    applyFail.add(1);
  }
}

// ─── Teardown: DB 정합성 검증 ─────────────────────────────────
export function teardown(data) {
  if (!data) return;

  const { matchId, creatorToken } = data;

  const matchRes = http.get(
    `${BASE_URL}/api/matches/${matchId}`,
    { headers: { 'Authorization': `Bearer ${creatorToken}` } }
  );

  if (matchRes.status === 200) {
    const matchData = matchRes.json('data');
    const totalFilled = (matchData.slots || []).reduce((sum, slot) => sum + (slot.filled || 0), 0);
    const isFullyBooked = matchData.isFullyBooked;

    console.log('\n========================================');
    console.log('  동시성 테스트 결과 요약');
    console.log('========================================');
    console.log(`ST 정원:      ${SLOT_REQUIRED}명`);
    console.log(`승인 완료:    ${totalFilled}명`);
    console.log(`정원 초과 차단: ${isFullyBooked ? '✅ 통과 (슬롯 마감 확인)' : '❌ 실패 (슬롯 미달)'}`);
    console.log(`정합성 검증:  ${totalFilled === EXPECTED_APPROVED ? '✅ 통과 (PASS)' : '❌ 실패 (FAIL)'}`);
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
