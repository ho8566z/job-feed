# job-feed

채용공고를 주기적으로 수집해 노션 DB로 전달하는 Spring Boot 서비스 (3차 개인 프로젝트).

고용24 공채속보 수집(@Scheduled) → (소스, 공고 ID) 기준 중복 제거 → send_log 대기열 → 노션 순차 전송(실패 시 최대 3회 재시도)

## 동작
- **수집**: 1시간마다 개발·IT 키워드(개발, IT, 엔지니어, AI, Back-end, 웹, 보안, 정보보호, 시스템, 개발자)로 공채속보를 조회해 합치고, 제목에 제외어(연구개발, 사업개발)가 있으면 버린다.
- **저장**: 처음 보는 공고만 `job`에 저장하고 `send_log`를 PENDING으로 만든다.
- **전송**: 1분마다 PENDING을 오래된 순으로 최대 10건, 건 사이 400ms 간격으로 노션에 보낸다. 실패하면 횟수를 올리고 3회째에 FAILED. 429(속도 제한)·401·403은 횟수를 올리지 않고 그 실행을 멈춘다.
- **알림**: 앱은 노션 페이지만 만든다. 푸시는 노션 DB 자동화(새 페이지 추가 시 알림)를 노션에서 따로 설정한다.

## 패키지
| 패키지 | 역할 |
|---|---|
| `collect` | 고용24 호출·정규화(`WorknetClient`), 키워드 수집(`JobCollector`), 저장(`JobStoreService`), 수집 1회(`CollectService`) |
| `sendlog` | 전송 대기열 엔티티(`SendLog`)와 전송(`SendService`) |
| `notion` | 노션 페이지 생성(`NotionClient`) |
| `schedule` | 수집·전송 스케줄러 |
| `job`, `source` | 엔티티와 리포지토리 |

## 환경변수
값은 코드·로그·Git에 남기지 않는다.

| 이름 | 내용 | 없으면 |
|---|---|---|
| `WORKNET_API_KEY` | 고용24 OPEN-API 인증키(개인회원 신청, 공채속보) | 수집을 건너뜀 |
| `NOTION_TOKEN` | 노션 통합(Integration) 토큰 | 전송을 건너뜀 |
| `NOTION_DATABASE_ID` | 공고 DB ID (통합에 공유돼 있어야 함) | 전송을 건너뜀 |
| `DB_USERNAME`, `DB_PASSWORD`, `DB_URL` | mysql 프로필에서만 사용 (`DB_URL`은 생략 가능) | - |

노션 DB 속성 이름은 코드와 정확히 같아야 한다: 회사(제목), 직무(텍스트), 지역(선택), 마감일(날짜), 수집일(날짜), 링크(URL), 상태(선택, 값 "신규").

## 실행
- 기본(local): H2 인메모리 — `./gradlew bootRun` (앱을 끄면 데이터가 사라지고 다시 켜면 같은 공고가 새 공고로 처리된다)
- MySQL: `--spring.profiles.active=mysql` + 환경변수 `DB_USERNAME`, `DB_PASSWORD`(선택 `DB_URL`)
- 시작 10초 뒤 첫 수집, 20초 뒤 첫 전송이 실행된다.

## 설정 (`application.yml`)
| 키 | 기본값 | 뜻 |
|---|---|---|
| `app.scheduling.enabled` | true | false면 스케줄러를 끈다 |
| `app.collect.delay-ms` | 3600000 | 수집 주기 |
| `app.send.delay-ms` | 60000 | 전송 실행 주기 |
| `app.send.batch-size` | 10 | 한 번에 보낼 건수 |
| `app.send.interval-ms` | 400 | 전송할 때 건 사이 간격 |
| `worknet.keywords`, `worknet.exclude-words` | 위 동작 참고 | 제목 검색 키워드와 제외어 |

## 테스트
- `./gradlew test` — 외부 호출 없이 실행된다. 실제 호출 테스트는 환경변수가 없으면 건너뛴다.
- 실제 고용24 호출: `./gradlew test --tests "*WorknetLiveTest" -i` (`WORKNET_API_KEY` 필요, 호출 한도를 쓴다)
- 실제 노션 호출: `./gradlew test --tests "*NotionLiveTest"` (노션 DB에 테스트 페이지가 1건 생긴다. 직접 지운다)

## 알려진 한계
- 공채속보 응답에는 근무지역·직무가 없어 지역은 비어 있다(노션 지역 속성 비움). 개발 직군은 제목 키워드로만 거른다.
- 제목 키워드라 오탐과 누락이 있다(예: 여러 직군을 함께 뽑는 공고).
- 소스가 1개라 수집은 순차 실행이다. 동시 저장 시 유니크 위반 처리(건별 건너뛰기)는 미구현이다.
- 노션에 페이지를 만든 직후 DB 기록이 실패하면 같은 공고가 한 번 더 전송될 수 있다.
- 호출 한도는 확인하지 못했다. 이용허락(출처표시·상업적 이용금지·변경금지)에 따라 원문 링크를 함께 저장하지만 출처 표기 속성은 따로 없다.

## 브랜치
main(안정) ← dev(통합) ← feature/*
