# EnrollGate — 저장소 구조 & 브랜치 전략

> Status: Draft v0.1

---

## 1. 모노레포 vs 멀티레포 — 모노레포로 시작

**결정**: 처음엔 **단일 저장소(모노레포)**로 시작하고, 3단계(MSA 분리) 진입 시점에 멀티레포 전환 여부를 다시 판단합니다.

**이유**
- 아키텍처 문서에서 정한 로드맵상 1~2단계는 모놀리식으로 개발합니다. 처음부터 서비스별 저장소를 나누면 코드를 왔다갔다 옮기는 데 불필요한 시간이 듭니다.
- 최근 백엔드 트렌드에서도 "처음부터 MSA로 쪼개기보다, 모놀리식 안에서 도메인 경계를 명확히 나눠두고 필요할 때 분리하는" 방식(Spring Modulith 같은 접근)이 주목받고 있습니다. 아래 폴더 구조를 도메인 패키지 단위로 미리 나눠두면 이 흐름을 그대로 따라가는 셈이 됩니다.
- 3단계에서 실제로 서비스를 분리할 때, "도메인 패키지 → 독립 서비스"로 추출하는 과정 자체를 README/발표자료에 "왜 이 시점에 분리했는지" 스토리로 담을 수 있습니다.

---

## 2. 폴더 구조 (모놀리식 단계)

```
enrollgate/
├── .github/
│   ├── workflows/
│   │   └── ci.yml                  # 빌드 + 테스트 자동화 (CD는 5단계에서 추가)
│   ├── ISSUE_TEMPLATE/
│   │   ├── feature.md
│   │   └── bug.md
│   └── PULL_REQUEST_TEMPLATE.md
│
├── src/
│   ├── main/
│   │   ├── java/com/enrollgate/
│   │   │   ├── user/                # 추후 User Service로 분리될 도메인
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── repository/
│   │   │   │   └── domain/
│   │   │   ├── course/              # 추후 Course Service
│   │   │   │   └── ...
│   │   │   ├── enrollment/          # 추후 Enrollment Service (핵심 도메인)
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── repository/
│   │   │   │   ├── domain/
│   │   │   │   └── queue/           # 대기열 로직 (WebSocket, Redis 연동)
│   │   │   ├── ai/                  # 추후 AI Service (봇 탐지)
│   │   │   │   └── ...
│   │   │   └── common/              # 공통: JWT, 예외 처리, 설정
│   │   │       ├── config/
│   │   │       ├── exception/
│   │   │       └── security/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/        # Flyway 스키마 마이그레이션 스크립트
│   └── test/
│       └── java/com/enrollgate/     # 도메인별 테스트 (구조는 main과 동일하게 미러링)
│
├── docs/                            # PRD, ERD, 아키텍처 문서 (Notion 원본과 동기화)
│   ├── PRD.md
│   ├── ERD-API-Spec.md
│   └── Architecture.md
│
├── docker-compose.yml                # 로컬 개발용 (PostgreSQL, Redis)
├── Dockerfile
├── build.gradle
├── settings.gradle
└── README.md
```

**핵심 포인트**: `user`, `course`, `enrollment`, `ai`를 처음부터 **패키지 단위로 명확히 분리**해뒀기 때문에, 3단계에서 서비스를 쪼갤 때 패키지를 통째로 새 저장소로 옮기는 형태가 됩니다. 이게 "도메인 경계를 처음부터 설계에 반영했다"는 근거가 됩니다.

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
