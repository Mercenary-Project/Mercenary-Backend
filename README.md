# Mercenary Backend

Spring Boot backend for the Mercenary project.

## Profiles

- `dev`: local development
- `prod`: server deployment

Default profile is `dev`.

## Local Development

Requirements:
- Java 17
- MySQL
- Redis

Run with default `dev` profile:

```bash
./gradlew bootRun
```

Optional environment variables:

```bash
SPRING_PROFILES_ACTIVE=dev
DB_URL=jdbc:mysql://localhost:3307/mercenary?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=root
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=mercenary_high_project_secret_key_for_jwt_2025_fighting
KAKAO_CLIENT_ID=your-kakao-client-id
KAKAO_REDIRECT_URI=http://localhost:5173/login/callback
AUTH_DEV_LOGIN_ENABLED=true
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
```

## Production Deployment

Main differences in `prod`:
- `ddl-auto=validate`
- SQL logging disabled
- `dev-login` disabled
- DB/Redis/JWT/Kakao values must come from environment variables

Create an env file first:

```bash
cp .env.prod.example .env.prod
```

Fill in the real production values in `.env.prod`, then build and run with Docker Compose:

```bash
docker stop my-mysql my-redis
docker rm my-mysql my-redis
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

## Recommended Workflow

1. Keep `main` deployable.
2. Develop features in separate branches.
3. Test locally with `dev` profile.
4. Merge into `main`.
5. Deploy server with `prod` profile.

## Next Hardening Tasks

- Introduce DB migrations with Flyway or Liquibase
- Remove sample secrets from local defaults
- Add CI/CD pipeline
- Add Nginx / HTTPS / domain setup
