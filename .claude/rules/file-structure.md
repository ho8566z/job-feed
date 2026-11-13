---
paths:
  - "**/*.java"
  - "**/resources/**"
---
# 파일(패키지) 구조 (Java) — 개인 프로젝트용

> 2차 팀 프로젝트(white-eyes)의 package-structure 원칙을 단일 모듈 개인 프로젝트에 맞게 일반화한 것이다.

## 1. 원칙
- 패키지는 **도메인(기능) 단위**로 묶고, 계층은 도메인 안에서 나눈다. 프로젝트 전체를 `controller/`, `service/`로 먼저 나누지 않는다.
- 한 도메인은 다른 도메인에 대한 의존을 최소로 한다.
- 공통 코드는 `common/`에 두되, 여러 도메인이 함께 쓰는 것만 둔다. 아무 코드나 넣지 않는다.

## 2. 구조
```
{기본 패키지}/
├── {Project}Application.java
├── {도메인}/
│   ├── controller/   요청 받기, 입력 검증(@Valid), service 호출, 응답·화면 반환
│   ├── service/      비즈니스 로직, 트랜잭션, DTO ↔ 엔티티 변환
│   ├── repository/   데이터 접근(Spring Data JPA / MyBatis Mapper)
│   ├── dto/          {동작}Request / Response (record)
│   ├── entity/       JPA 엔티티
│   ├── exception/    도메인 예외
│   ├── client/       외부 서버 호출이 있을 때만
│   └── scheduler/    예약 작업이 있을 때만
├── config/           Security·Web·Scheduling 등 설정
└── common/           여러 도메인이 함께 쓰는 유틸·공통 응답·공통 예외
```
- 도메인이 작아 파일이 몇 개뿐이면 하위 패키지를 나누지 않고 한 패키지에 둬도 된다. 한 프로젝트 안에서는 한 가지 방식으로 통일한다.

## 3. 의존 규칙
- 계층 의존은 `controller → service → repository` 한 방향. controller가 repository를 직접 부르지 않는다. service는 HTTP 요청·응답 객체를 쓰지 않는다. scheduler는 service만 호출한다.
- 다른 도메인이 필요하면 그 도메인의 **service**만 부른다. 다른 도메인의 repository·엔티티를 직접 쓰지 않는다.
- 두 도메인이 서로를 부르는 순환이 생기면 설계를 다시 보거나 공통 부분을 `common/`으로 내린다.

## 4. DTO·엔티티
- 엔티티는 controller 밖으로 내보내지 않는다. 요청·응답은 항상 DTO이고, 변환은 service에서 한다.
- 요청 DTO와 응답 DTO를 나눈다. JSON 필드 이름은 camelCase.

## 5. 리소스
- `resources/templates/{도메인}/`, `static/js|css/`, 설정은 `application.yml` + 프로필별 `application-{profile}.yml`.
- 비밀값은 저장소에 올리지 않는다(`.gitignore`, 예시는 `*.example`만 커밋).
- 화면 JS는 페이지마다 진입 파일 하나, 공용 기능은 모듈로 나눠 `import`한다. 서버 호출은 공용 모듈 한 곳을 거친다.
