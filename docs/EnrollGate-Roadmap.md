# EnrollGate — 개발 계획 (1~5단계 로드맵)

> Status: v0.1 (1단계 완료 시점 기준 작성)
> 요구사항은 [PRD](EnrollGate-PRD.md), 설계 근거는 [Architecture](EnrollGate-Architecture.md), API/ERD 세부는 [ERD-API-Spec](EnrollGate-ERD-API-Spec.md) 참고. 이 문서는 그 위에서 **"다음에 뭘, 어떤 순서로, 어떤 제약을 감안해서 할지"**를 정리한 실행 계획 문서다. 각 단계가 끝날 때마다 이 문서의 체크박스와 상태를 갱신한다.

---

## 0-1. 프로젝트 성격 (범위 결정에 영향)

**졸업 전 학습/포트폴리오 목적의 개인 프로젝트다. 실제 배포(AWS) 계획은 현재 없다.** 이에 따라:
- 5단계의 "Dockerfile/CI 작성"까지는 포트폴리오 가치가 있어 진행 대상이지만, **실제 AWS 리소스 생성·배포는 당분간 하지 않는다** (계정/비용 이슈와 무관하게 우선순위 밖)
- Docker/WSL2처럼 시스템을 크게 바꾸는 설치는 하지 않기로 결정 — 대신 Redis/PostgreSQL 포터블(zip) 배포판 + k6로 2~4단계와 5단계의 "코드/CI 작성"까지 전부 커버

### 로컬 인프라 준비 완료 (2026-07-24)

Docker/WSL2 없이 아래 세 가지로 로컬 개발 환경을 구성했다. 관리자 권한 필요 없음, 설치 마법사 없음.

| 도구 | 방식 | 위치/설치 | 계정/포트 |
|---|---|---|---|
| Redis 5.0.14.1 (tporadowski) | 포터블 zip, 압축 해제 후 바로 실행 | `C:\Users\win11\devtools\redis` | 포트 6379 |
| PostgreSQL 17.10 (EDB binaries) | 포터블 zip, `initdb` + `pg_ctl`로 실행 | `C:\Users\win11\devtools\postgresql` | `enrollgate/enrollgate`, 포트 5432 (docker-compose.yml과 동일) |
| k6 2.1.0 | winget 무인 설치 | `winget install GrafanaLabs.k6` | - |

실제 앱(`./gradlew bootRun`)을 이 세 가지에 붙여서 회원가입→로그인→관리자 과목등록→수강신청 전체 플로우와 k6 부하테스트(5 VU, 25 iterations, 100% 성공)까지 검증 완료. 이 과정에서 버그 2개를 발견해 고쳤다:
- `GlobalExceptionHandler`가 처리되지 않은 예외를 로그 없이 삼키던 문제 (이제 `log.error`로 서버 로그에 스택트레이스가 남는다)
- 대기열 순번(`QueueService.position`) 계산이 `entered_at < 자기_자신의_entered_at`로 비교하다 보니, H2에서 타임스탬프 저장 정밀도 차이로 방금 삽입한 행이 "자기 자신보다 이전"으로 잘못 카운트되는 경계 버그. `id` 기반 비교(`countByCourseIdAndStatusAndIdLessThan`)로 교체해 타임스탬프 정밀도와 무관하게 동작하도록 수정 — 실제 앱을 띄워 재현했기 때문에 발견할 수 있었던 버그다.

> Redis/Postgres는 세션 종료 후에도 백그라운드 프로세스로 남아있지 않을 수 있으니, 다음 세션에서 필요하면 README의 "Docker 없이 로컬에서 띄우기" 명령으로 다시 시작한다.

## 0. 현재 상태 요약

- **1단계 완료**: 단일 서비스, 비관적 락 기반 정원 제어, DB 폴링 수준 대기열(진입/승격/확정/만료 스케줄러), 회원가입·로그인·과목 조회·관리자 과목 등록, 테스트 48개 통과 (동시성 테스트 포함).
- **2단계 완료**: Redis 원자 연산 전략 추가(설정으로 비관적 락과 전환 가능), k6 A/B 실측, WebSocket 대기열 push. 테스트 59개 통과. 멀티 인스턴스 간 Redis Pub/Sub 동기화만 3단계 이후로 남음.
- **3단계 완료(논리적 분리)**: Gradle 멀티모듈로 서비스 경계를 컴파일 타임에 강제(`common`/`user-service`/`course-service`/`enrollment-service`/`ai-service`/`app`). 물리적 분리(별도 프로세스/DB, API Gateway)는 범위 밖으로 확정. 테스트 65개 통과.
- **4단계 완료**: Redis Streams 기반 비동기 봇 탐지 이벤트 파이프라인, 휴리스틱(기본) + Isolation Forest(선택, Python FastAPI 서비스) 이중 스코어러, `GET /admin/bot-detection/logs`. 테스트 137개 통과.
- **로컬 개발 환경 제약** (2026-07-18 확인, 이후 바뀔 수 있음):
  - Docker 없음 (데몬 자체가 없음) → Testcontainers, docker-compose 실행 불가
  - PostgreSQL/Redis 네이티브 설치 없음 → 테스트는 H2로 대체 실행 중, 실제 앱 실행은 여전히 docker-compose 전제
  - JDK 21만 설치됨 (17 없음) → `build.gradle` 툴체인 21로 조정된 상태 유지
  - k6 미설치, 단 `winget`으로 설치 가능
  - Python 3.14 + pip는 설치되어 있음 (4단계 AI 서비스에 활용 가능)
  - GitHub remote(`origin` → `github.com/rspstat/EnrollGate`)는 연결되어 있어 push 시 실제 Actions 실행 가능
- 이 제약들 때문에 **코드 작성은 전 단계 다 가능**하지만, "실제로 띄워서 수치를 뽑는" 검증은 일부 단계(특히 2단계 성능 비교, 5단계 배포)에서 인프라 준비가 먼저 필요하다. 각 단계 항목에 영향 표시.

---

## 1. 2단계 — 성능 개선 (k6 부하테스트 → Redis 원자 연산 전환, WebSocket 대기열)

### 목표
비관적 락(A) vs Redis 원자 연산(B)을 동일 조건에서 부하테스트로 비교해 개선율을 수치로 증명하고, 대기열 순번 안내를 WebSocket 실시간 push로 전환한다.

### 작업 항목 — 전부 완료 (2026-07-24)
1. [x] k6 설치 및 baseline 시나리오 작성 (`code/backend/k6/enroll-load-test.js`) — 실제 신청 폭주 시나리오로 TPS/응답시간 측정
2. [x] Redis 원자 연산(Lua Script) 구현 — `scripts/reserve_seat.lua` + `RedisSeatGate`. 기존 비관적 락 전략과 나란히 유지, `enrollment.concurrency-strategy=pessimistic-lock|redis-atomic` 설정으로 런타임 전환 (`EnrollmentReservationStrategy` 인터페이스 + `@ConditionalOnProperty` 택일 빈 2개)
3. [x] Redis-DB 정합성 동기화 — 신청(증가)은 Redis EVAL 성공 시 단일 원자 `UPDATE`로 즉시 반영. 취소/만료(감소)는 항상 비관적 락 경로만 사용하기로 **의도적으로 범위를 좁힘**(README/코드 주석에 트레이드오프 명시) — 완전한 양방향 동기화는 하지 않음
4. [x] k6 A/B 부하테스트 실행 — 결과를 README "동시성 제어 전략" 절에 기록. **고경합(정원 5석/동시 150명)에서 Redis가 평균 34% 빠름, 저경합(정원 20석/동시 100명)에서는 오히려 Redis가 느림** — 경합 정도에 따라 유불리가 갈린다는 것이 핵심 결론
5. [x] WebSocket 대기열 push 구현 — `/ws/queue/{courseId}?token=`, `QueueWebSocketHandler`/`QueueSessionRegistry`/`QueueNotificationService`. 승격 시 `YOUR_TURN`, 만료 시 `EXPIRED`, 순번 변동 시 `POSITION_UPDATE` 브로드캐스트. 기존 폴링 API는 폴백으로 유지
6. [x] WebSocket 인증 — 브라우저 네이티브 WS API가 커스텀 헤더를 못 보내는 제약 때문에 쿼리 파라미터(`?token=`)로 JWT 전달, `QueueHandshakeInterceptor`가 핸드셰이크 단계에서 검증 (Spring Security는 `/ws/**`를 permitAll로 열어두고 이 인터셉터에 인증을 위임)
7. [ ] Redis Pub/Sub을 통한 멀티 인스턴스 간 순번 이벤트 동기화 — **실제 다중 인스턴스 환경이 있어야 검증 가능**하므로 미착수. 현재 구현은 단일 인스턴스(`QueueSessionRegistry`가 JVM 로컬 메모리) 기준

### 검증
- 새 로직 단위 테스트: `PessimisticLockReservationStrategyTest`, `RedisAtomicReservationStrategyTest` (Mockito)
- 실제 로컬 Redis로 Lua 스크립트 원자성 자체를 검증: `RedisSeatGateIntegrationTest` (동시 50 요청 vs 정원 10 → 정확히 10건만 성공, Redis 없으면 자동 스킵)
- 실제 임베디드 서버 + 진짜 WebSocket 연결로 승격 push 검증: `QueueWebSocketFlowTest`
- 전체 테스트 59개 통과 (기존 48개 + 2단계 신규 11개)

### 환경 제약 및 대응
- ~~Redis 서버가 없어 실측 불가~~ → **해결됨**. 포터블 Redis(`C:\Users\win11\devtools\redis`)로 로컬 실측 가능 (위 "로컬 인프라 준비 완료" 참고)

---

## 2. 3단계 — MSA 분리 (User / Course / Enrollment / AI) — 완료 (논리적 분리, 2026-07-24)

### 목표
현재 패키지 경계(user/course/enrollment/ai)를 실제 독립 서비스로 추출한다. **범위 결정**: 물리적 분리(별도 프로세스/DB/네트워크 통신, API Gateway)까지는 하지 않고, **Gradle 멀티모듈로 컴파일 타임에 서비스 경계를 강제하는 "논리적 분리"**로 한정했다 — 졸업 전 포트폴리오 프로젝트 규모에 맞춘 의도적 스코프 축소.

### 작업 항목 — 전부 완료
1. [x] 모노레포 유지 vs 멀티레포 전환 결정: **모노레포 유지 + Gradle 멀티모듈**로 확정
2. [x] 서비스별 DB 분리 방식 결정: **논리적 분리만**(같은 Postgres 인스턴스/스키마 공유) — 물리적 분리는 범위 밖
3. [x] User Service 추출 (`user-service` 모듈) — 인증/JWT 발급
4. [x] Course Service 추출 (`course-service` 모듈) — 과목 CRUD + `CourseCapacityPort`의 실제 구현(정원 동시성 제어 어댑터 2종)
5. [x] Enrollment Service 추출 (`enrollment-service` 모듈) — 신청/취소/대기열. **Course 엔티티/리포지토리를 전혀 참조하지 않음**
6. [x] AI Service 모듈 스캐폴드 생성 (`ai-service`, 로직은 4단계에서)
7. [x] API Gateway: **범위 밖으로 확정** (물리적으로 분리된 서비스가 없어 불필요)
8. [x] 서비스 간 통신 확정: 물리적 분리를 안 하므로 REST/메시지 큐 대신 **포트 인터페이스(common.contract) + Spring DI**로 대체 — `CourseCapacityPort`(enrollment→course), `QueueLengthPort`(course→enrollment)
9. [x] 서비스 분리 과정과 그 안에서 발견한 버그를 이 문서 + README + Architecture 문서에 기록

### 실제로 겪은 문제와 해결
- **순환 의존**: enrollment는 정원 예약을 위해 course 데이터가 필요하고(enrollment→course), course는 목록에 대기열 길이를 표시하려고 enrollment 데이터가 필요하다(course→enrollment). 양방향 직접 참조는 Gradle이 순환 모듈 의존으로 거부 → `common.contract`에 포트 인터페이스 2개를 두고 각자 어댑터만 제공하는 방식으로 해결
- **동시성 버그 발견**: 리팩터링 중 `EnrollmentService.enroll()`이 (잠금 없는) 스냅샷 조회 후 (잠금 있는) 예약을 시도하도록 짰더니, 같은 트랜잭션 안의 Hibernate 1차 캐시 때문에 실제 DB 락이 걸리지 않아 동시성 테스트(정원 3석, 동시 15건)가 15건 전부 성공으로 실패했다. `attemptReservation()` 하나로 스냅샷+예약을 원자적으로 합쳐 수정 — Architecture 문서 "3단계 구현 메모" 참고
- 정원 동시성 제어 로직(`PessimisticLockReservationStrategy`/`RedisAtomicReservationStrategy`)을 enrollment-service에서 course-service로 재배치(`PessimisticLockCourseCapacityAdapter`/`RedisAtomicCourseCapacityAdapter`) — "정원 카운터는 Course의 책임"이라는 원칙에 맞춤

### 검증
- 전체 테스트 65개 통과 (2단계 59개 + 3단계 신규 어댑터 테스트 등)
- 두 전략(pessimistic-lock, redis-atomic) 모두 실제 Postgres/Redis에 붙여 signup→login→관리자 과목등록→enroll→queue 전체 플로우 재검증

### 환경 제약 및 대응
- 여러 서비스를 로컬에서 동시에 띄우는 것 자체는 Docker 없이도 포트를 나눠 `bootRun`으로 가능하나, 이번엔 물리적 분리 자체를 하지 않기로 했으므로 해당 없음

---

## 3. 4단계 — AI 봇 탐지 연동 — 완료 (2026-07-24)

### 목표
매크로/봇 의심 요청을 탐지하고 관리자가 로그를 확인할 수 있게 한다.

### 작업 항목 — 전부 완료
1. [x] 탐지 피처 정의: 요청 간격(`intervalSeconds`), 1분 내 반복횟수(`repeatedCount1Min`), User-Agent 이상치(`userAgentSuspicious`) — Redis 키(`last-request`/`req-count`, TTL 기반)로 상태 없이 계산 (PRD 6.4)
2. [x] 알고리즘 확정: **Isolation Forest**(scikit-learn) — 기본값은 여전히 휴리스틱 규칙 스코어러이고, 설정(`ai.scorer=isolation-forest`)으로 강화 경로 선택 가능 (PRD Open Question 해소)
3. [x] 구현 방식 결정: **Java 휴리스틱(기본) + 별도 Python(FastAPI + scikit-learn) 서비스(선택)** 하이브리드. Python 서비스가 꺼져 있거나 응답 실패 시 자동으로 휴리스틱 폴백 — 봇 탐지는 부가 기능이라 이 경로가 신청 자체를 막으면 안 됨
4. [x] Enrollment → AI Service 비동기 이벤트 발행 — **Redis Streams**(`enrollgate:enroll-events`)로 확정(Kafka 대비 로컬 인프라 최소화). `EnrollEventPublisher`가 신청 성공/큐잉/이미신청 등 모든 결과에 fire-and-forget으로 발행(예외를 절대 던지지 않음), `EnrollEventConsumer`가 Consumer Group(`XREADGROUP`)으로 5초 주기 폴링
5. [x] `BotDetectionLog` JPA 엔티티/리포지토리 추가 (V1 마이그레이션의 `bot_detection_logs` 테이블에 매핑, `request_features`는 `@JdbcTypeCode(SqlTypes.JSON)`으로 JSONB 저장)
6. [x] `GET /api/v1/admin/bot-detection/logs` 엔드포인트 구현 (최신 50건, Admin 전용)
7. [x] 정탐/오탐 검증: 실제 파이프라인으로 "정상 첫 신청"(낮은 점수, 미탐지) vs "짧은 간격 반복 + 의심 UA 봇 패턴"(높은 점수, FLAGGED)을 재현해 구분되는지 확인 — 아래 "실제로 겪은 문제와 해결" 참고. 대량 라벨링 데이터 기반의 정식 정탐/오탐률 측정은 실제 트래픽이 없어 범위 밖

### 실제로 겪은 문제와 해결
- **Java→Python HTTP 422 버그**: `PythonModelBotDetectionScorer`가 `RestTemplate`로 FastAPI `/score`를 호출할 때마다 동일한 페이로드가 curl로는 성공하는데 Java에서는 `"Field required": "body"` 422로 실패했다. Python(uvicorn) 로그에 `Unsupported upgrade request` 경고가 함께 찍힌 것이 단서였다. 원인: `ai-service`의 클래스패스에 Apache HttpClient/OkHttp/Jetty가 전혀 없어 Spring Boot의 `RestTemplateBuilder`가 **JDK `java.net.http.HttpClient` 기반 팩토리**를 기본 선택했는데, 이 클라이언트는 평문 HTTP 요청에도 기본적으로 HTTP/2 cleartext(h2c) 업그레이드를 시도한다. uvicorn(h11)은 이 업그레이드 요청을 이해하지 못해 커넥션을 오염시키고 이후 요청까지 깨뜨렸다. `RestTemplateBuilder.requestFactory(SimpleClientHttpRequestFactory::new)`로 구식 `HttpURLConnection` 기반(HTTP/1.1 전용) 팩토리를 명시적으로 강제해 해결 — `Connection: close` 헤더를 추가하는 최초 시도는 증상을 완화하지 못했고, 근본 원인은 HTTP/2 업그레이드 협상 자체였다
- **IsolationForest sentinel 값 학습 분포 버그**: 위 HTTP 버그를 고친 뒤 실제 신청으로 검증하던 중, 정상 사용자와 봇 의심 사용자의 첫 신청이 **똑같은 점수**로 나오는 걸 발견했다. 원인은 Java 쪽에서 "이전 요청 없음"을 `interval_seconds=-1.0` sentinel로 보내는데, Python 모델의 학습 데이터(`code/ai-model/main.py`)는 전부 "이전 요청이 있던" 정상 케이스(간격 2~60초)만 담고 있어서, -1이라는 값 자체가 학습 분포 밖의 극단치가 되어 **실제 봇 신호(반복횟수, UA)와 무관하게 첫 신청을 무조건 이상치로 판정**했다. 학습 데이터에 "정상적인 첫 신청"(interval=-1, 낮은 반복횟수, 정상 UA가 대부분) 샘플을 추가해 sentinel 값 자체가 이상치가 되지 않도록 보정 — 이후 "정상 첫 신청"(미탐지) vs "짧은 간격 반복 + 의심 UA"(FLAGGED, suspicion_score↑)가 명확히 구분됨을 실측으로 확인

### 검증
- 신규 단위/통합 테스트: `HeuristicBotDetectionScorerTest`, `PythonModelBotDetectionScorerTest`(`MockRestServiceServer`), `EnrollEventConsumerTest`, `EnrollEventPublisherTest`(실제 로컬 Redis, 없으면 자동 스킵), `AdminBotDetectionControllerTest`
- 전체 테스트 137개 통과 (실행 방식에 따라 중복 집계될 수 있으나 실패/에러 0건)
- 실제 Postgres/Redis/Python(FastAPI+uvicorn) 3개 프로세스를 모두 띄운 상태로 회원가입→로그인→관리자 과목등록→수강신청(정상 UA/봇 의심 UA)→`GET /admin/bot-detection/logs` 전체 플로우 재현. 봇 의심 사용자가 짧은 간격으로 6회 연속 신청 시도 시 `repeatedCount1Min`이 오르며 매번 `suspicionScore≈0.57`, `actionTaken=FLAGGED`로 정확히 탐지됨을 확인

---

## 4. 5단계 — Docker + CI/CD + AWS 배포 — 완료(Dockerfile/CI 작성까지, 2026-07-24)

### 작업 항목
1. [x] 백엔드 앱 자체 Dockerfile 작성(`code/backend/app/Dockerfile`) — 멀티 스테이지 빌드(`eclipse-temurin:21-jdk-jammy`로 `:app:bootJar` 빌드 → `eclipse-temurin:21-jre-jammy` 런타임에 jar만 복사, 이미지 크기 최소화)
2. [x] docker-compose에 앱 서비스 추가 — `postgres`/`redis`에 healthcheck를 붙이고 `app`이 `depends_on: condition: service_healthy`로 기다리도록 구성. `ai-model`(Python FastAPI, 선택적 강화 스코어러) 서비스도 함께 추가 — 꺼져 있어도 `ai-service`가 자동으로 휴리스틱 스코어러로 폴백하므로 필수는 아님
3. [x] `.github/workflows/ci.yml` 작성 — 빌드 + 테스트 자동화. **주의**: Repo-Strategy 문서 초안은 이 파일을 `code/backend/.github/`에 두는 것처럼 그려져 있었으나, GitHub Actions는 **저장소 루트**의 `.github/workflows/`만 인식하므로 실제로는 저장소 루트(`EnrollGate/.github/workflows/ci.yml`)에 위치시켰다 — Repo-Strategy 문서도 이에 맞춰 수정
4. [x] GitHub Actions에서 실제 이미지 빌드까지 확인 — push해서 실제로 돌려봄. **1차 시도는 `build-and-test`에서 실패**했고(원인/수정은 아래 "실제로 겪은 문제와 해결" 참고), 수정 후 재push한 2차 실행에서 `build-and-test`/`docker-build` 두 잡 모두 성공 확인
5. [x] ~~AWS 배포(ECS/EKS)~~ — **범위 밖으로 확정**. 졸업 전 학습/포트폴리오 목적 프로젝트라 실제 배포 계획이 없음. Dockerfile/CI까지가 5단계의 실질적 완료 기준

### 검증
- 로컬 `./gradlew test`로 코드 변경 없음(Dockerfile/compose/CI yaml만 추가)을 확인 — 137개 테스트 전부 통과 유지
- Docker/`docker build` 자체를 로컬에서 실행할 수 없어(Docker 미설치), Dockerfile 문법과 `docker-compose.yml` 구조는 직접 검토로 확인
- **실제 GitHub Actions에서 실행해 CI를 검증함** — 1차 push 후 `build-and-test` 잡이 실패했고, 로그를 받아 원인을 찾아 수정한 뒤 로컬에서 재현·검증 완료. 2차 push 후 `build-and-test`(테스트 137개 통과) + `docker-build`(app/ai-model 이미지 빌드 성공) 두 잡 모두 성공(success)으로 완료됨을 실제 Actions 실행 결과로 확인

### 실제로 겪은 문제와 해결
- **CI 전용 테스트 실패 (`EnrollEventPublisherTest`)**: 로컬에서는 항상 Redis가 떠 있어 몰랐지만, Redis가 없는 GitHub Actions 러너에서 처음 돌려보니 `EnrollEventPublisherTest`의 4개 테스트가 전부 `RedisConnectionFailureException`으로 실패했다. 원인은 `assumeTrue(reachable, ...)`가 `@BeforeEach`에서 Redis 연결 실패 시 테스트를 스킵시키긴 하지만, **JUnit5는 `@BeforeEach`의 assumption이 실패해도 `@AfterEach`(`tearDown()`)는 여전히 실행한다**는 점을 놓쳤다는 것 — `tearDown()`이 `redisTemplate != null`만 확인하고 무조건 `redisTemplate.delete(...)`를 호출하다 보니, "스킵됐어야 할 테스트"가 tearDown의 Redis 호출 때문에 실패로 둔갑했다. `redisReachable` 플래그를 필드로 저장해 `tearDown()`도 같은 조건으로 가드하도록 수정 — 로컬 Redis를 일부러 내려서 재현 후 수정, 재기동해서 재검증 완료. 같은 패턴을 쓰는 `RedisSeatGateIntegrationTest`는 `tearDown()`이 `courseId != null`(assumeTrue 통과 후에만 할당됨)로 우연히 이미 안전했다

### 환경 제약 및 대응
- Docker 미설치 → 로컬 빌드/실행 검증 대신 GitHub Actions CI에서 빌드 검증 (러너에 Docker 기본 내장)
- GitHub remote는 연결되어 있어 push 시 실제 Actions가 돌아간다 → push는 매번 사용자 확인 후 진행
- AWS 배포는 이 프로젝트 범위에 포함하지 않음

---

## 5. 진행 순서 제안

기본적으로 PRD/Architecture 로드맵 순서(2→3→4→5)를 따르되, 단계 내부에서 **"코드로 짤 수 있는 것"과 "인프라가 있어야 검증되는 것"을 분리**해서 막히지 않고 계속 진행한다:

- 2단계: Redis 전략 코드 + WebSocket은 먼저 작성 → k6/Redis 설치 여부에 따라 실측 비교는 별도 확인 후 진행 — **완료**
- 3단계: 순수 리팩토링/구조 변경이라 막히는 지점이 거의 없음(단, 순환 의존/동시성 회귀는 실제로 발생했음) — **완료(논리적 분리)**
- 4단계: Redis Streams + 휴리스틱/Isolation Forest 이중 스코어러로 완료. 실측 과정에서 HTTP 클라이언트 버그와 모델 학습 데이터 버그를 발견해 수정 — **완료**
- 5단계: Dockerfile/docker-compose/CI YAML 작성 완료. 실제 GitHub Actions 실행 검증(push)만 사용자 확인 대기 — **코드/설정 작성 완료, 실배포는 범위 밖**

## 6. 프로젝트 현황 요약 (2026-07-24)

1~5단계 모두 "코드/설정으로 짤 수 있는 부분"은 완료되었다. 남은 것은 오직 GitHub Actions push를 통한 CI 실행 검증(사용자 확인 필요)뿐이며, 이는 범위 밖으로 확정한 실제 AWS 배포와는 무관한 항목이다. 이번 프로젝트가 처음부터 목표로 삼은 "동시성 제어 실측 비교, MSA 논리적 분리, AI 봇 탐지, CI 자동화"라는 4가지 포트폴리오 스토리는 전부 실제 로컬 인프라(Postgres/Redis/Python)에 붙여 재현 가능한 형태로 검증되었다.

각 단계 완료 시 이 문서의 체크박스와 README "진행 상황"을 함께 갱신한다.
