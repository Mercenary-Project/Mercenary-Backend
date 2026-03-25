# Mercenary Backend

축구 용병 매칭 서비스 `Mercenary`의 Spring Boot 백엔드입니다.

현재 백엔드는 아래 흐름을 중심으로 동작합니다.

- 카카오 로그인 / 개발용 로그인
- 경기 게시글 생성, 조회, 수정, 삭제
- 경기 신청, 승인, 거절, 취소
- 내가 올린 경기 / 내가 신청한 경기 조회
- 지난 경기 게시글 숨김 및 주기적 삭제
- JWT 인증 및 만료 응답 규칙 정리
- 테스트 환경 분리 및 GitHub Actions CI 적용

## 기술 스택

- Java 17
- Spring Boot 3
- Spring Security
- Spring Data JPA
- MySQL
- Redis / Redisson
- Swagger (springdoc-openapi)
- JUnit 5 / Mockito / H2
- GitHub Actions

## 프로필

- `dev`: 로컬 개발용
- `test`: 테스트 전용
- `prod`: 운영 배포용

기본 실행 프로필은 `dev`입니다.

## 주요 기능

### 인증

- 카카오 OAuth 로그인
- 개발 환경에서만 `dev-login` 허용
- Access Token 기반 인증
- Access Token 유효시간 1시간
- 인증 실패 응답 통일
  - `TOKEN_MISSING`
  - `TOKEN_INVALID`
  - `TOKEN_EXPIRED`

### 매치

- 경기 게시글 생성 / 조회 / 수정 / 삭제
- 내 경기 목록 조회
- 주변 경기 조회
- 지난 경기 게시글은 조회 응답에서 제외
- 지난 경기 게시글은 스케줄러가 주기적으로 삭제

### 신청

- 경기 신청
- 신청 상태 조회
- 신청 취소
- 신청 목록 조회
- 신청 승인 / 거절
- 이미 종료된 경기, 마감된 경기, 중복 신청은 서버에서 차단

### 품질 / 운영

- 테스트 전용 프로필 분리
- 로컬 DB 없이 전체 테스트 실행 가능
- GitHub Actions CI로 빌드 / 테스트 자동 검증
- 운영 프로필에서 `ddl-auto=validate`

## 로컬 실행

### 필요 환경

- Java 17
- MySQL
- Redis

### 필수 환경변수

로컬 `dev` 실행 시 최소 아래 값이 필요합니다.

```text
SPRING_PROFILES_ACTIVE=dev
DB_URL=jdbc:mysql://localhost:3307/mercenary?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=mercenary
DB_PASSWORD=your-db-password
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=your-local-jwt-secret-key-with-enough-length
KAKAO_CLIENT_ID=your-kakao-client-id
KAKAO_REDIRECT_URI=http://localhost:5173/login/callback
AUTH_DEV_LOGIN_ENABLED=true
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
APP_TIMEZONE=Asia/Seoul
```

IntelliJ 실행 설정에서는 환경변수를 공백이 아니라 `;` 로 구분해서 넣어야 합니다.

예시:

```text
SPRING_PROFILES_ACTIVE=dev;DB_URL=jdbc:mysql://localhost:3307/mercenary?serverTimezone=Asia/Seoul&characterEncoding=UTF-8;DB_USERNAME=mercenary;DB_PASSWORD=your-db-password;REDIS_HOST=localhost;REDIS_PORT=6379;JWT_SECRET=your-local-jwt-secret-key-with-enough-length;KAKAO_CLIENT_ID=your-kakao-client-id;KAKAO_REDIRECT_URI=http://localhost:5173/login/callback;AUTH_DEV_LOGIN_ENABLED=true;APP_CORS_ALLOWED_ORIGINS=http://localhost:5173;APP_TIMEZONE=Asia/Seoul
```

### 실행

```bash
./gradlew bootRun
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

## 테스트

테스트는 `test` 프로필로 분리되어 있으며, H2와 mock 기반으로 동작합니다.

전체 테스트 실행:

```bash
./gradlew test
```

현재 테스트 환경 특징:

- 테스트 전용 DB는 H2 사용
- Redis / Redisson은 테스트에서 mock 처리
- 로컬 MySQL / Redis 상태에 의존하지 않도록 분리

## CI

GitHub Actions CI가 적용되어 있습니다.

동작:

- `main` 브랜치 push 시 실행
- `main` 대상 Pull Request 생성 / 갱신 시 실행
- 빌드 검증
- 전체 테스트 실행

워크플로 파일:

- [`.github/workflows/ci.yml`](/C:/Users/user/Mercenary-Backend-main/.github/workflows/ci.yml)

## 운영 배포

운영은 `prod` 프로필과 Docker Compose 기준으로 동작합니다.

운영 특징:

- `ddl-auto=validate`
- `open-in-view=false`
- `dev-login` 비활성화
- DB / Redis / JWT / Kakao / CORS 값은 환경변수로 주입

### 운영용 환경파일 생성

```bash
cp .env.prod.example .env.prod
```

`.env.prod`에 실제 운영 값을 입력한 뒤 실행합니다.

### 운영 실행

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

### 헬스체크

```bash
curl http://localhost:8080/actuator/health
```

관련 파일:

- [`src/main/resources/application-prod.yml`](/C:/Users/user/Mercenary-Backend-main/src/main/resources/application-prod.yml)
- [`docker-compose.prod.yml`](/C:/Users/user/Mercenary-Backend-main/docker-compose.prod.yml)
- [`.env.prod.example`](/C:/Users/user/Mercenary-Backend-main/.env.prod.example)

## 인증 응답 규칙

인증이 필요한 API에서 토큰 문제 발생 시 아래 규칙으로 응답합니다.

토큰 없음:

```json
{
  "code": "TOKEN_MISSING",
  "message": "인증 토큰이 없습니다."
}
```

토큰 만료:

```json
{
  "code": "TOKEN_EXPIRED",
  "message": "만료된 토큰입니다."
}
```

토큰 이상:

```json
{
  "code": "TOKEN_INVALID",
  "message": "유효하지 않은 토큰입니다."
}
```

## 현재 정리된 운영 이슈

- 지난 경기 게시글은 조회에서 숨김 처리
- 지난 경기 게시글은 스케줄러로 주기적 삭제
- 신청 시간은 `Asia/Seoul` 기준으로 통일
- 인증 토큰 유효시간 1시간으로 조정
- 예외 응답을 `400 / 403 / 404 / 409` 의미에 맞게 분리
- 운영 설정에서 자동 스키마 변경 제거

## 다음 작업 후보

- Flyway 또는 Liquibase 도입
- 백엔드 CD 도입
- 운영 배포 문서 보강
- 알림 기능 추가
- README에 아키텍처 다이어그램 추가
