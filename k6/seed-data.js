import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://localhost:8080';

export const options = {
  vus: 10,           
  iterations: 5000,  
};

export function setup() {
  // 1. 생성자 로그인
  const res = http.post(
    `${BASE_URL}/api/auth/dev-login`,
    JSON.stringify({ kakaoId: 7777, nickname: '데이터-생성자' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status !== 200) {
    console.error('로그인 실패');
    return null;
  }
  return { token: res.json('data.accessToken') };
}

export default function (data) {
  if (!data) return;

  const i = __ITER;
  const res = http.post(
    `${BASE_URL}/api/matches`,
    JSON.stringify({
      title: `데이터 시딩 매치 ${i}`,
      content: `성능 테스트를 위한 대량 데이터 ${i}번입니다.`,
      placeName: `테스트 경기장 ${i}`,
      district: '강남구',
      matchDate: '2030-01-01T10:00',
      maxPlayerCount: 22,
      currentPlayerCount: 1,
      latitude: 37.5 + (Math.random() * 0.1),
      longitude: 127.0 + (Math.random() * 0.1),
      fullAddress: `서울특별시 강남구 테스트로 ${i}`,
    }),
    { headers: { 
      'Content-Type': 'application/json', 
      'Authorization': `Bearer ${data.token}` 
    } }
  );

  check(res, { '매치 생성 성공': (r) => r.status === 201 });
}
