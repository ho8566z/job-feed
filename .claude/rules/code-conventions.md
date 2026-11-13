---
paths:
  - "**/*.java"
---
# 코드 컨벤션 (Java) — 개인 프로젝트용

> 2차 팀 프로젝트(white-eyes) 규칙에서 프로젝트 전용 내용(기능 ID, 내부 포트, 모듈, decisions 번호, CI)을 빼고 일반화한 것이다.
> 코드를 쓰거나 고치기 전에 이 문서와 `file-structure.md`를 읽고 따른다.

## 1. 공통
- 가독성이 먼저다. 이름만 보고 역할과 의도를 알 수 있게 쓴다. 한 파일 안에서는 같은 스타일을 유지한다.
- 들여쓰기 공백 4칸(Tab 혼용 금지, YAML은 2칸). UTF-8, LF, 파일 끝 줄바꿈 1개, 줄 끝 공백 없음.
- 쓰지 않는 import는 지운다. 와일드카드 import(`import x.*`)는 쓰지 않는다. 순서: 표준 라이브러리 → 외부 라이브러리 → 프로젝트 내부.
- 주석은 '왜 그렇게 했는지'를 쓴다. 주석 처리한 옛 코드는 남기지 않는다(기록은 git).
- 함수·메서드 하나가 여러 책임을 지지 않게 한다. 매직 넘버·매직 문자열은 상수로 빼고, 운영 중 바뀔 수 있는 값은 설정(`application.yml`·환경변수)으로 읽는다.
- 빈 `catch`로 예외를 삼키지 않는다. 예외는 처리하는 한 곳에서만 로그로 남긴다(남기고 다시 던지지 않는다).

## 2. 이름
- 변수·메서드 camelCase, 클래스·인터페이스·Enum PascalCase, 상수 `static final` UPPER_SNAKE_CASE, 패키지 소문자.
- 의미 없는 축약(`cnt`, `tmp`, `data2`)을 쓰지 않는다. 반복문 `i` 같은 관례는 예외.
- 메서드는 동사 + 목적어(`findJobById`). Boolean은 `is`·`has`·`can`으로 시작한다.
- 클래스 접미사

| 역할 | 접미사 |
|---|---|
| 화면(템플릿) 컨트롤러 | `PageController` |
| REST API 컨트롤러 | `ApiController` |
| 서비스 | `Service` |
| JPA 리포지토리 | `Repository` |
| 외부 호출 클라이언트 | `Client` |
| 예약 작업 | `Scheduler` |
| 요청 DTO | `{동작}Request` |
| 응답 DTO | `Response` |
| 예외 | `Exception` |

## 3. 작성 규칙
- 중괄호는 선언문과 같은 줄에 쓰고, 한 줄짜리 `if`에도 중괄호를 쓴다.
- 의존성은 생성자 주입으로 받는다(`@RequiredArgsConstructor` + `private final` 필드). `@Autowired` 필드 주입은 쓰지 않는다.
- DTO는 `record`를 기본으로 한다. 엔티티는 `record`로 만들지 않는다.
- 엔티티에 public setter를 열지 않는다. 상태 변경은 의미 있는 메서드로 한다(`job.close()`).
- 날짜·시각은 `java.time`. `Optional`은 반환값에만 쓴다.
- 문자열 비교는 `"RUNNING".equals(status)`처럼 상수 쪽에서 하거나 Enum으로 한다.
- `System.out.println`, `e.printStackTrace()`를 쓰지 않는다. 로거를 쓴다.
- 클래스 안 순서: 상수 → 필드 → 생성자 → public 메서드 → protected·package-private → private.

## 4. Lombok (허용 목록)
- 쓰는 것: `@RequiredArgsConstructor`(빈 생성자 주입), `@Slf4j`(로거), `@Getter`(엔티티), `@NoArgsConstructor(access = AccessLevel.PROTECTED)`(엔티티), `@Builder`(생성자 또는 record에만).
- 쓰지 않는 것: `@Setter`·`@Data`·`@Value`, 엔티티의 `@ToString`·`@EqualsAndHashCode`, 복합키 ID 클래스 밖의 `@AllArgsConstructor`.

## 5. 테스트
- JUnit 5. 클래스 `{대상}Test`, 메서드는 한국어 `@DisplayName`으로 무엇을 확인하는지 쓴다.

## 6. 로깅
- 로거는 `@Slf4j`(필드 `log`). `LoggerFactory.getLogger`로 직접 선언하지 않는다.
- 메시지는 파라미터 방식(`log.info("수집 완료 count={}", n)`). 문자열 더하기 금지.
- 레벨: ERROR(사람이 봐야 하는 실패), WARN(실패했지만 재시도·복구 중), INFO(운영에 의미 있는 1회성 상태 변화), DEBUG(개발용 상세). 반복 처리는 INFO로 남기지 않는다.
- 예외는 스택과 함께 한 곳에서 `log.error("…", e)`. 예상된 업무 예외는 스택 없이 WARN.
- 남기지 않는 것: 비밀번호·토큰·API 키·세션 ID, 개인 정보, 요청·응답 본문 전체.

## 7. 이 프로젝트(job-feed)의 예외
- `SendService`는 생성자 안에서 `TransactionTemplate`을 만들기 때문에 `@RequiredArgsConstructor` 대신 생성자를 직접 쓴다(생성자 안에 로직이 있는 경우).
- 스프링 테스트는 `src/test/resources/junit-platform.properties`의 `spring.test.constructor.autowire.mode=all` 설정으로 생성자 주입(`@RequiredArgsConstructor` + `private final`)을 쓴다. `@Autowired` 필드 주입은 쓰지 않는다.
