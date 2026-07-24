# EnrollGate — 저장소 구조 & 브랜치 전략

> Status: Draft v0.1

---

## 1. 모노레포 vs 멀티레포 — 모노레포 유지로 확정 (2026-07-24)

**결정**: 3단계(MSA 분리)에 진입했지만 **모노레포를 그대로 유지**하고, 서비스 경계는 **Gradle 멀티모듈**로 강제한다. 완전히 별도 저장소/별도 배포 단위로 쪼개는 "물리적 분리"는 하지 않기로 확정했다 (졸업 전 학습/포트폴리오 프로젝트 규모에 맞춰 범위를 좁힌 결정, `docs/EnrollGate-Roadmap.md` 참고).

**이유**
- 1~2단계는 계획대로 모놀리식으로 개발했다.
- 3단계에서 "패키지 단위 분리 → Gradle 서브프로젝트 분리"로 전환했다: 각 모듈이 독립된 `build.gradle`/컴파일 단위를 가지므로, 서비스 경계를 넘는 참조는 **컴파일 에러로 즉시 드러난다** — 코드 리뷰나 컨벤션이 아니라 빌드 자체가 경계를 강제한다.
- 순수하게 물리적으로 완전히 분리하면(별도 저장소, 별도 실행 프로세스, 실제 네트워크 통신) 이번 범위에서 얻는 이득 대비 작업량이 너무 커서, "논리적 분리"(한 프로세스로 실행되지만 모듈 경계는 진짜로 강제됨) 수준에서 멈추기로 했다.

---

## 2. 폴더 구조 (3단계: Gradle 멀티모듈)

```
enrollgate/code/backend/
├── settings.gradle                  # 6개 서브프로젝트 include
├── build.gradle                     # 공통 설정(툴체인, Lombok, BOM import 등)을 subprojects{} 블록으로 전파
│
├── common/                          # 공통: JWT, 예외 처리, Security 설정, 서비스 간 포트 인터페이스
│   └── src/main/java/com/enrollgate/common/
│       ├── config/                  # WebSocket 관련 설정 등 (도메인 특정 config는 각 서비스 모듈에 위치)
│       ├── contract/                 # CourseCapacityPort, QueueLengthPort — 서비스 간 순환 의존 방지용 포트
│       ├── exception/
│       └── security/
│
├── user-service/                    # User 도메인 (회원가입/로그인/JWT 발급)
│   └── src/main/java/com/enrollgate/user/
│       ├── controller/ service/ repository/ domain/
│
├── course-service/                  # Course 도메인 + CourseCapacityPort의 실제 구현(어댑터 2종)
│   └── src/main/java/com/enrollgate/course/
│       ├── controller/ service/ repository/ domain/
│       └── service/PessimisticLockCourseCapacityAdapter.java, RedisAtomicCourseCapacityAdapter.java, RedisSeatGate.java
│
├── enrollment-service/              # Enrollment 도메인(핵심) — Course 엔티티/리포지토리를 전혀 참조하지 않음
│   └── src/main/java/com/enrollgate/enrollment/
│       ├── controller/ service/ repository/ domain/
│       ├── queue/                   # 대기열 로직 (DB 폴링 + WebSocket)
│       └── queue/websocket/         # QueueSessionRegistry, QueueNotificationService, QueueWebSocketConfig
│
├── ai-service/                      # 4단계 스캐폴드 (아직 로직 없음)
│
├── app/                             # 실행 가능한 조립 지점 — 위 5개 모듈을 전부 implementation으로 모아 부팅
│   └── src/main/java/com/enrollgate/
│       ├── EnrollgateApplication.java
│       └── resources/ application.yml, db/migration/  (Flyway 마이그레이션은 app이 소유)
│   └── src/test/java/com/enrollgate/api/              # 여러 도메인을 가로지르는 통합 테스트(MockMvc, WebSocket, 동시성)는 app에 위치
│
├── k6/                               # 부하테스트 스크립트 (Gradle 모듈 아님)
├── app/Dockerfile                    # 멀티 스테이지 빌드(gradle → JRE 21 슬림 런타임), 5단계 구현 완료
├── docker-compose.yml                # 로컬 개발용 (PostgreSQL, Redis, ai-model, app 전부 포함, 5단계 구현 완료)
├── gradlew, gradlew.bat, gradle/

enrollgate/                           # 저장소 루트
├── .github/
│   └── workflows/ci.yml             # 빌드 + 테스트 자동화(GitHub Actions는 저장소 루트의 .github만 인식하므로
│                                       code/backend가 아닌 여기 위치 — 5단계 구현 완료). CD(실배포)는 범위 밖
└── code/backend/, code/ai-model/, code/frontend/, docs/, README.md
```

**`code/frontend/`**: 5단계 완료 이후 추가된 수동 테스트용 정적 페이지(순수 HTML/CSS/JS, 빌드 도구 없음). 이 프로젝트의 포트폴리오 산출물은 어디까지나 백엔드이며, 이 폴더는 브라우저로 API를 눈으로 확인하기 위한 보조 도구일 뿐 로드맵 단계에는 포함되지 않는다. 자세한 실행 방법은 README "수동 테스트용 프론트엔드" 절 참고.

**핵심 포인트**:
- `enrollment-service`의 `build.gradle`은 `course-service`에 의존하지 않는다 — Course 정원 데이터/카운터는 `common.contract.CourseCapacityPort`(enrollment→course 방향)로만 주고받고, 대기열 길이는 `common.contract.QueueLengthPort`(course→enrollment 방향)로만 주고받는다. 두 포트 모두 `common`에 정의되어 있어 어느 쪽도 서로를 직접 참조하지 않는다.
- `@SpringBootTest`로 전체 컨텍스트가 필요한 테스트(`EnrollmentConcurrencyTest`, `*ApiTest`, `QueueWebSocketFlowTest`)는 `EnrollgateApplication`을 가진 `app` 모듈에만 둘 수 있다 — 다른 모듈에는 부트스트랩할 설정 클래스가 없기 때문. 순수 Mockito 단위 테스트는 각자의 모듈에 남는다.
- 이 구조 자체가 "3단계에서 실제로 물리적 서비스로 분리한다면 어떤 모듈을 어떤 순서로 꺼내면 되는지"를 보여주는 근거가 된다 — `user-service`/`course-service`/`enrollment-service`를 각각 별도 Spring Boot 앱으로 만들고 포트 인터페이스를 REST 호출로 바꾸면 된다.

---

## 3. 브랜치 전략 — GitHub Flow 채택

**결정**: Git Flow처럼 무거운 전략 대신, 단순한 **GitHub Flow**를 사용합니다.

```
main (항상 배포 가능한 상태 유지, 브랜치 보호 규칙 적용)
 └─ feature/enrollment-lock          (기능 단위 브랜치)
 └─ feature/queue-websocket
 └─ fix/course-count-race-condition
 └─ perf/redis-atomic-migration      (성능 개선 작업은 perf/ 접두사)
```

**GitHub Flow를 선택한 이유**
- 혼자 또는 소규모로 진행하는 졸업 프로젝트에는 `develop`/`release` 브랜치까지 두는 Git Flow가 과합니다.
- 대신 각 로드맵 단계(1~5단계)가 끝날 때마다 **태그(`v0.1-phase1`, `v0.2-phase2` 등)**를 남기면, Git Flow의 릴리즈 관리 이점을 훨씬 가볍게 가져갈 수 있습니다.

**브랜치 이름 규칙**
| 접두사 | 용도 |
|---|---|
| `feature/` | 신규 기능 |
| `fix/` | 버그 수정 |
| `perf/` | 성능 개선 (동시성 제어 방식 A→B 전환 등) |
| `refactor/` | 구조 개선, 동작 변경 없음 |
| `docs/` | 문서 변경 |

---

## 4. 커밋 컨벤션 — Conventional Commits

```
<type>(<scope>): <subject>

예시:
feat(enrollment): 정원 초과 시 대기열 진입 로직 구현
fix(course): 잔여 정원 조회 시 동시성 이슈 수정
perf(enrollment): 정원 카운터 제어를 Redis 원자 연산으로 전환
docs(readme): 부하테스트 결과 섹션 추가
test(enrollment): 동시 신청 100건 시나리오 테스트 추가
```

| Type | 용도 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `perf` | 성능 개선 (이 프로젝트에서 특히 자주 쓰일 타입) |
| `refactor` | 리팩토링 |
| `test` | 테스트 추가/수정 |
| `docs` | 문서 |
| `chore` | 빌드 설정, 의존성 등 |

> `perf` 타입 커밋들을 모아두면 나중에 "성능 개선 히스토리"를 커밋 로그만으로도 보여줄 수 있어서, 이 프로젝트에서는 의식적으로 잘 활용하는 게 좋습니다.

---

## 5. PR 템플릿 (`.github/PULL_REQUEST_TEMPLATE.md`)

```markdown
## 변경 사항
-

## 관련 이슈
Closes #

## 테스트
- [ ] 단위 테스트 작성/통과
- [ ] 로컬에서 동시 요청 시나리오 검증 (해당하는 경우)

## 성능 영향 (해당하는 경우)
- 변경 전:
- 변경 후:

## 체크리스트
- [ ] 셀프 코드 리뷰 완료
- [ ] 문서(docs/) 업데이트 필요 여부 확인
```

---

## 6. 이슈 라벨 체계

| 라벨 | 용도 |
|---|---|
| `phase-1` ~ `phase-5` | PRD/아키텍처 로드맵 단계 매핑 |
| `type: feature` / `type: bug` / `type: perf` | 작업 유형 |
| `priority: high/medium/low` | 우선순위 |
| `domain: user/course/enrollment/ai` | 도메인 영역 |

> `phase-*` 라벨을 마일스톤과 연동하면, GitHub Milestones 기능으로 "1단계 진행률 80%" 같은 걸 자동으로 보여줄 수 있어 README나 발표자료에 진행 상황을 정량적으로 넣기 좋습니다.

---

## 7. 마일스톤 (GitHub Milestones ↔ PRD 로드맵 연동)

| Milestone | 대응 단계 |
|---|---|
| `v0.1 - Core Logic` | 1단계: 단일 서비스, 비관적 락 기반 정원 제어 |
| `v0.2 - Performance` | 2단계: 부하테스트, Redis 전환, WebSocket |
| `v0.3 - MSA` | 3단계: 서비스 분리 |
| `v0.4 - AI` | 4단계: 봇 탐지 연동 |
| `v0.5 - Deploy` | 5단계: CI/CD + AWS 배포 |

---

## 8. Open Questions

- [ ] 브랜치 보호 규칙: PR 승인 필수 여부 (1인 개발이라면 셀프 머지 허용, 대신 CI 통과는 필수로)
- [ ] `docs/` 폴더와 Notion 중 어디를 단일 소스(source of truth)로 둘지 — 권장: Notion에서 작성 후 마일스톤 완료 시점마다 `docs/`에 스냅샷 동기화
