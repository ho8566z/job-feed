# job-feed

채용공고를 주기적으로 수집해 노션 DB로 전달하는 Spring Boot 서비스 (3차 개인 프로젝트).

수집(@Scheduled, 비동기) → URL 기준 중복 제거 → send_log 대기열 → 노션 순차 전송(실패 시 최대 3회 재시도)

## 실행
- 기본(local): H2 인메모리 — `./gradlew bootRun`
- MySQL: `--spring.profiles.active=mysql` + 환경변수 `DB_USERNAME`, `DB_PASSWORD`(선택 `DB_URL`)

## 브랜치
main(안정) ← dev(통합) ← feature/*
