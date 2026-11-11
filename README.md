# job-feed

![job-feed 표지](https://raw.githubusercontent.com/ho8566z/job-feed_word/main/images/ppt/cover-banner.png)

채용공고를 주기적으로 수집해 노션 DB로 전달하는 Spring Boot 서비스 (3차 개인 프로젝트, 2026.11).

명세서와 발표 자료는 문서 저장소 [job-feed_word](https://github.com/ho8566z/job-feed_word)에 있습니다.

## 프로젝트 요약

- **해결하려는 문제**: 채용공고가 여러 곳에 흩어져 매일 직접 찾아야 하고, 이미 본 공고를 구분하기 어렵다. 단순 연동은 외부 API의 속도 제한·장애에 취약하다.
- **핵심 기능**: 수집 스케줄러, 중복 제거, send_log 대기열 전송, 재시도·실패 격리, 노션 상태 보드·마감일순 뷰
- **기술 스택**: Java 17, Spring Boot, Spring Data JPA, H2·MySQL, 고용24 OPEN-API, Notion API, JUnit

고용24 공채속보 수집(@Scheduled) → (소스, 공고 ID) 기준 중복 제거 → send_log 대기열 → 노션 순차 전송(실패 시 최대 3회 재시도)

## 구조

![서비스 구성도](https://raw.githubusercontent.com/ho8566z/job-feed_word/main/images/ppt/architecture.png)

![ERD](https://raw.githubusercontent.com/ho8566z/job-feed_word/main/images/ppt/erd.png)

## 동작
- **수집**: 1시간마다 개발·IT 키워드(개발, IT, 엔지니어, AI, Back-end, 웹, 보안, 정보보호, 시스템, 개발자)로 공채속보를 조회해 합치고, 제목에 제외어(연구개발, 사업개발, 설비, 공정, 정비, 경비, 영업, 마케팅, 사무직)가 있으면 버린다. 제외어 때문에 "영업 시스템 개발" 같은 공고가 빠질 수 있다.
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
- **file(실제 사용 권장)**: H2 파일 DB — `./gradlew bootRun --args="--spring.profiles.active=file"`. 앱을 껐다 켜도 공고와 전송 대기열이 남아 노션에 중복 전송되지 않는다. 데이터는 `./data/jobfeed.mv.db`(실행 폴더 기준)에 저장되고 환경변수 `JOBFEED_DATA_DIR`로 위치를 바꿀 수 있다. 같은 파일을 두 프로세스가 동시에 열 수 없으므로 앱은 한 번에 하나만 실행한다.
- 기본(local): H2 인메모리 — `./gradlew bootRun` (앱을 끄면 데이터가 사라지고 다시 켜면 같은 공고가 새 공고로 처리된다. 개발·테스트용)
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

## 내부 API (시연·점검용)
인증이 없어 서버를 `127.0.0.1`에만 묶어 두었다. 외부에 공개하지 않는다.

| 요청 | 설명 |
|---|---|
| `POST /api/v1/collect` | 수집을 즉시 한 번 실행하고 `{"sources":[{"name","fetched","created","duplicated"}]}`를 돌려준다. 이미 수집 중이면 409, 수집 실패나 인증키 없음이면 500 |
| `GET /api/v1/send-logs?status=&page=&size=` | 전송 상태별 건수(`counts`)와 목록(`items`, 최신순). `status`는 PENDING/SUCCESS/FAILED(생략 시 전체), `size` 기본 20·최대 100. 잘못된 값은 400 |

```powershell
curl.exe -X POST http://127.0.0.1:8080/api/v1/collect
curl.exe "http://127.0.0.1:8080/api/v1/send-logs?status=FAILED"
```
수동 수집으로 쌓인 공고는 전송 스케줄러가 1분 안에 노션으로 보낸다.

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

## 문서

- 문서 저장소: [job-feed_word](https://github.com/ho8566z/job-feed_word) (명세서 엑셀 14시트, ERD·다이어그램, 발표 자료 PPT·PDF)

## 브랜치
main(안정) ← dev(통합) ← feature/*
