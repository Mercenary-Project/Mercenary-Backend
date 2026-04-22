# k6 동시성 제어 & 부하 테스트

Mercenary 백엔드의 **Redisson 분산 락 기반 동시성 제어**가 실제 HTTP 요청에서도 정상 동작하는지 검증하는 k6 테스트입니다.

## 📋 테스트 시나리오

| 스크립트 | 목적 | VU | 검증 포인트 |
|---------|------|-----|-----------|
| `concurrency-test.js` | 동시성 제어 정합성 | 100 | 9자리에 100명 동시 신청 → 정확히 9명만 성공 |
| `load-test.js` | API 성능 측정 | 0→50 | p95 응답시간, 처리량, 에러율 |

## 🚀 실행 방법

### 1. 테스트 환경 기동

```bash
# 프로젝트 루트에서 실행
docker compose -f k6/docker-compose.k6.yml up --build -d

# 앱이 완전히 뜰 때까지 대기 (healthcheck)
docker compose -f k6/docker-compose.k6.yml logs -f app
# "Started MercenaryApplication" 메시지가 보이면 Ctrl+C
```

### 2. 동시성 테스트 실행

```bash
k6 run k6/concurrency-test.js
```

**기대 결과:**
- `apply_success`: 정확히 **9** (= maxPlayerCount 10 - initialCount 1)
- `apply_fail`: 정확히 **91** (= 100 - 9)
- teardown에서 `currentPlayerCount == 10` 확인

### 3. 부하 테스트 실행

```bash
k6 run k6/load-test.js
```

**기대 결과:**
- `http_req_duration{p(95)}` < 3초
- `error_rate` < 15%

### 4. 테스트 환경 정리

```bash
docker compose -f k6/docker-compose.k6.yml down
```

## 📊 결과 해석

### 동시성 테스트 출력 예시

```
✅ 동시성 테스트 결과:
매치 정원:    10명
현재 인원:    10명
기대 인원:    10명
정합성 검증:  ✅ PASS

✓ apply_success....: 9   ✓
✓ apply_fail.......: 91  ✓
```

### 부하 테스트 주요 메트릭

| 메트릭 | 설명 | 기준 |
|--------|------|------|
| `http_req_duration` | 전체 요청 응답시간 | p95 < 3s |
| `match_apply_duration` | 매치 신청 응답시간 | p95 < 2s |
| `match_list_duration` | 매치 목록 조회 응답시간 | p95 < 1s |
| `error_rate` | 에러 비율 | < 15% |

## ⚙️ 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `BASE_URL` | `http://localhost:8080` | 테스트 대상 서버 URL |

```bash
# 다른 서버를 대상으로 테스트할 경우
k6 run -e BASE_URL=http://192.168.1.100:8080 k6/concurrency-test.js
```

## 🔧 사전 요구사항

- **Docker Desktop**: 테스트 환경 기동용
- **k6**: `winget install k6` 또는 [공식 설치 가이드](https://k6.io/docs/get-started/installation/)
