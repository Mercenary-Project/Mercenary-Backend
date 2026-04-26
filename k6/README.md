# k6 성능 테스트 (동시성 제어 · 부하 · 캐시)

Mercenary 백엔드의 **Redisson 분산 락 기반 동시성 제어**, **API 부하 성능**, **Redis 캐시 효과**를 k6로 측정하고 Grafana로 시각화합니다.

## 📋 테스트 시나리오

| 스크립트 | 목적 | VU | 검증 포인트 |
|---------|------|-----|-----------|
| `concurrency-test.js` | 동시성 제어 정합성 | 100 | 9자리에 100명 동시 신청 → 정확히 9명만 성공 |
| `load-test.js` | API 부하 성능 측정 | 0→50 | p95 응답시간, 처리량, 에러율 |
| `cache-read-test.js` | Redis 캐시 효과 측정 | 0→100 | 캐시 Hit 시 p95 응답시간 200ms 이하 |

---

## 🚀 실행 방법

### 1. 테스트 환경 기동

```bash
# 프로젝트 루트에서 실행 (앱 + MySQL + Redis + InfluxDB + Grafana 일괄 기동)
docker compose -f k6/docker-compose.k6.yml up --build -d

# 앱이 완전히 뜰 때까지 대기 (healthcheck 통과 확인)
docker compose -f k6/docker-compose.k6.yml logs -f app
# "Started MercenaryApplication" 메시지 확인 후 Ctrl+C
```

### 2. Grafana 접속

브라우저에서 **http://localhost:3000** 접속 (로그인 불필요 - 익명 Admin)

좌측 메뉴 → Dashboards → **"Mercenary k6 성능 테스트 대시보드"** 선택

> k6 실행 중 5초마다 자동 갱신됩니다.

---

### 3. 동시성 제어 테스트

```bash
# Grafana 드롭다운에서 "concurrency" 선택 시 이 테스트 데이터만 표시
k6 run --out influxdb=http://localhost:8086/k6 k6/concurrency-test.js
```

**기대 결과:**

| 메트릭 | 기대값 | 의미 |
|--------|--------|------|
| `apply_success` | **9** | 분산 락이 초과 신청을 정확히 차단 |
| `apply_fail` | **91** | 락 경합으로 인한 409 응답 |
| teardown `currentPlayerCount` | **10** | DB 정합성 최종 검증 |

---

### 4. 부하 테스트

```bash
# Grafana 드롭다운에서 "load" 선택 시 이 테스트 데이터만 표시
k6 run --out influxdb=http://localhost:8086/k6 k6/load-test.js
```

**임계값 (thresholds):**

| 메트릭 | 기준 |
|--------|------|
| `http_req_duration` p95 | < 3,000ms |
| `match_list_duration` p95 | < 1,000ms |
| `match_apply_duration` p95 | < 2,000ms |
| `error_rate` | < 15% |

---

### 5. 캐시 효과 측정 테스트

```bash
# Grafana 드롭다운에서 "cache" 선택 시 이 테스트 데이터만 표시
k6 run --out influxdb=http://localhost:8086/k6 k6/cache-read-test.js
```

**테스트 구조 (Miss vs Hit 비교):**

| Phase | 시간 | VU | 측정 내용 |
|-------|------|----|---------|
| Phase 1 (cache_miss) | 0s ~ 30s | 50 | 캐시 없는 파라미터 → DB 직접 조회 |
| Phase 2 (cache_hit)  | 40s ~ 2m | 0→100 | 워밍업된 파라미터 → Redis 캐시 Hit |

- **Miss**: `size=11~19` (캐시 미적재) / `matchId` 워밍업 범위 밖
- **Hit**: `size=20`, `page=0` (setup에서 워밍업 완료) / `matchId` 0~99번

**임계값 (thresholds):**

| 메트릭 | 기준 | 의미 |
|--------|------|------|
| `match_list_miss_duration` p95 | < 800ms | DB 직접 조회 허용 범위 |
| `match_list_hit_duration` p95  | < 80ms  | Redis 캐시 Hit 목표 |
| `match_detail_miss_duration` p95 | < 500ms | DB 직접 조회 허용 범위 |
| `match_detail_hit_duration` p95  | < 80ms  | Redis 캐시 Hit 목표 |
| `error_rate` | < 1% | 안정성 |

> 테스트 완료 후 터미널에 Miss/Hit p95 비교표가 출력됩니다.
> TTFB(`timings.waiting`)를 측정하므로 네트워크 전송 시간은 제외됩니다.

---

### 6. 테스트 환경 정리

```bash
docker compose -f k6/docker-compose.k6.yml down
```

---

## 📊 HTML 리포트

각 테스트 완료 후 프로젝트 루트에 HTML 리포트가 생성됩니다.

| 파일 | 해당 테스트 |
|------|-----------|
| `concurrency_summary.html` | 동시성 제어 테스트 |
| `summary.html` | 부하 테스트 |
| `summary-cache-read.html` | 캐시 효과 측정 테스트 |

---

## ⚙️ 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `BASE_URL` | `http://localhost:8080` | 테스트 대상 서버 URL |

```bash
# 다른 서버를 대상으로 테스트
k6 run --out influxdb=http://localhost:8086/k6 \
  -e BASE_URL=http://192.168.1.100:8080 \
  k6/concurrency-test.js
```

---

## 🔧 사전 요구사항

- **Docker Desktop**: 테스트 환경 기동용
- **k6**: `winget install k6` 또는 [공식 설치 가이드](https://k6.io/docs/get-started/installation/)
