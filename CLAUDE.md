# EnrollGate — Project Context

## 프로젝트 개요
EnrollGate는 대학 수강신청 시스템의 트래픽 폭주, 정원 초과 판매, 매크로 남용 문제를 해결하는 백엔드 시스템이다. 취업 포트폴리오 목적의 개인 프로젝트이며, 핵심은 **동시성 제어 + 성능 개선 스토리를 정량적으로 증명하는 것**이다.

자세한 기획/설계는 `docs/` 폴더 참고:
- `docs/PRD.md` — 요구사항 명세 (기능/비기능 요구사항, 로드맵)
- `docs/ERD-API-Spec.md` — ERD, API 명세
- `docs/Architecture.md` — 서비스 분리 기준, 동시성 제어 전략 비교
- `docs/Repo-Strategy.md` — 브랜치/커밋/이슈 컨벤션

## 폴더 구조
```
EnrollGate/
├── docs/          # 기획/설계 문서
├── assets/        # README용 다이어그램, 스크린샷
└── code/
    ├── backend/   # Spring Boot 프로젝트 (실제 작업 대부분 여기)
    └── frontend/  # 데모용 최소 프론트엔드 (기능 검증용, 스타일링에 시간 쓰지 말 것)
```

**중요**: Gradle/빌드 명령은 반드시 `code/backend/`에서 실행. 루트에서 `./gradlew` 실행 시도하지 말 것. 프론트엔드 작업은 `code/frontend/`에서.

**현재 상태**: `code/backend/`, `code/frontend/` 둘 다 비어 있음 (아직 프로젝트 스캐폴딩 전). 빌드/린트/테스트 명령은 아직 존재하지 않으므로, 이 파일에 임의로 지어내지 말 것 — Gradle 프로젝트가 생성되면 실제 명령(`./gradlew build`, `./gradlew test` 등)을 여기에 추가할 것.

## 기술 스택
- 언어/프레임워크: Java + Spring Boot
- DB: PostgreSQL
- 캐시/락/대기열: Redis
- 인증: JWT
- 실시간 통신: WebSocket (Spring WebSocket)
- 부하테스트: k6
- 컨테이너: Docker
- CI/CD: GitHub Actions → AWS (ECS/EKS)

## 개발 로드맵 (현재 단계 표시 필수)
- [ ] 1단계: 단일 서비스, 비관적 락(JPA `@Lock`) 기반 정원 제어, 대기열 기본 구현
- [ ] 2단계: k6 부하테스트 → Redis 원자 연산(Lua Script) 전환 → A/B 성능 비교, WebSocket 대기열
- [ ] 3단계: User/Course/Enrollment/AI 서비스로 MSA 분리
- [ ] 4단계: AI 봇 탐지 연동
- [ ] 5단계: Docker + GitHub Actions + AWS 배포

> 현재 어느 단계를 진행 중인지 대화 시작 시 알려줄 것. 완료된 단계는 위 체크박스를 직접 체크해서 최신 상태로 유지.

## 핵심 설계 원칙 (반드시 지킬 것)
1. **정합성 우선**: 응답 속도보다 정원 정확성이 우선이다. 락/원자 연산 없이 카운터를 갱신하는 코드는 절대 작성하지 않는다.
2. **패키지 = 미래의 서비스 경계**: `user`, `course`, `enrollment`, `ai` 패키지 경계를 넘나드는 직접 참조를 만들지 않는다. 서로 다른 도메인 패키지 간에는 인터페이스나 이벤트로만 통신한다 (나중에 MSA로 분리할 것을 전제).
3. **Redis 용도 구분**: 동시성 제어(Lua Script) / 대기열(Sorted Set) / 캐싱(TTL)을 명확히 분리해서 구현하고, 코드에도 용도를 주석으로 명시한다.
4. **성능 개선은 `perf` 커밋으로**: 동시성 제어 방식을 바꾸거나 캐싱을 추가할 때는 반드시 `perf(scope): 설명` 형태의 커밋으로 남기고, 가능하면 변경 전/후 수치를 커밋 메시지나 PR에 남긴다.
5. **프론트엔드는 최소 구현**: `code/frontend/`는 백엔드 로직을 데모/검증하는 용도로만 존재한다. UI 스타일링, 상태관리 구조 등에 시간을 쓰지 않는다. 로그인 → 과목 조회 → 신청 버튼 → 결과 표시 정도의 최소 플로우만 구현한다.

## 커밋/브랜치 컨벤션
- 브랜치: `feature/`, `fix/`, `perf/`, `refactor/`, `docs/` 접두사 사용
- 커밋: Conventional Commits (`feat`, `fix`, `perf`, `refactor`, `test`, `docs`, `chore`)
- PR 템플릿: `.github/PULL_REQUEST_TEMPLATE.md` 참고 (성능 영향 섹션 반드시 작성)

## 작업 시 주의사항
- 정원 카운터 관련 로직을 수정할 때는 동시 요청 시나리오(예: 100명 동시 신청)를 가정하고 테스트 코드를 먼저 생각한다.
- 아직 확정되지 않은 사항은 각 문서의 "Open Questions" 섹션에 있음 — 임의로 결정하지 말고 먼저 확인/질문할 것.
- Spring Modulith 스타일의 모듈 경계를 의식하며 개발 (3단계 MSA 분리를 염두에 둔 구조 유지).