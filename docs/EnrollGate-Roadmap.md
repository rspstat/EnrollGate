# EnrollGate — 개발 계획 (1~5단계 로드맵)

> Status: v0.1 (1단계 완료 시점 기준 작성)
> 요구사항은 [PRD](EnrollGate-PRD.md), 설계 근거는 [Architecture](EnrollGate-Architecture.md), API/ERD 세부는 [ERD-API-Spec](EnrollGate-ERD-API-Spec.md) 참고. 이 문서는 그 위에서 **"다음에 뭘, 어떤 순서로, 어떤 제약을 감안해서 할지"**를 정리한 실행 계획 문서다. 각 단계가 끝날 때마다 이 문서의 체크박스와 상태를 갱신한다.

---

## 0. 현재 상태 요약

- **1단계 완료**: 단일 서비스, 비관적 락 기반 정원 제어, DB 폴링 수준 대기열(진입/승격/확정/만료 스케줄러), 회원가입·로그인·과목 조회·관리자 과목 등록, 테스트 48개 통과 (동시성 테스트 포함).
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

### 작업 항목
1. [ ] k6 설치(`winget install k6`) 및 baseline 시나리오 작성 — 현재 비관적 락 구현 대상으로 동시 신청 부하 생성, TPS·응답시간·에러율 측정
2. [ ] Redis 원자 연산(Lua Script) 구현 — `EVAL`로 "잔여 정원 확인 + 원자적 감소"를 한 번에 처리하는 전략을 추가하고, 기존 비관적 락 전략과 **나란히 유지**(런타임 전환 가능하게, 예: `enrollment.concurrency-strategy=pessimistic-lock|redis-atomic`)해야 A/B 비교가 가능
3. [ ] Redis 카운터와 Postgres 간 최종 정합성 동기화 방식 구현 (예: 신청 확정 시 비동기로 DB 반영 + 주기적 검증 배치)
4. [ ] k6로 A/B 동일 시나리오 부하테스트 실행, 결과를 README "동시성 제어 전략" 표와 이 문서에 기록
5. [ ] WebSocket 대기열 push 구현 (Spring WebSocket) — 기존 폴링 API(`GET /queue/status`)는 폴백으로 유지, `EnrollResponse`에 `websocketUrl` 필드 추가
6. [ ] WebSocket 세션 관리(연결 수 제한, 재연결 처리) — 단일 인스턴스 기준으로 우선 구현
7. [ ] Redis Pub/Sub을 통한 멀티 인스턴스 간 순번 이벤트 동기화는 **실제 다중 인스턴스 환경이 있어야 검증 가능** → 코드/설계는 준비하되, 로컬 단일 인스턴스에서는 동작 검증까지만

### 환경 제약 및 대응
- Redis 서버가 없어 원자 연산 코드는 짤 수 있어도 로컬에서 실측은 불가 → Windows 호환 Redis(Memurai 등) 설치 또는 Docker 확보가 선행되어야 진짜 A/B 수치를 뽑을 수 있음. 설치 여부는 진행 직전에 확인받는다.

---

## 2. 3단계 — MSA 분리 (User / Course / Enrollment / AI)

### 목표
현재 패키지 경계(user/course/enrollment/ai)를 실제 독립 서비스로 추출하고, API Gateway를 통해 라우팅한다.

### 작업 항목
1. [ ] 모노레포 유지 vs 멀티레포 전환 결정 (Repo-Strategy 문서에서 "3단계 진입 시점에 재판단"하기로 되어 있던 항목 — 이번에 확정)
2. [ ] 서비스별 DB 분리 방식 결정: 처음부터 물리적 DB 분리 vs 스키마만 논리적 분리 (Architecture Open Question)
3. [ ] User Service 추출 — 인증/JWT 발급 전담, 다른 서비스는 JWT 서명 검증만 수행 (공개키 공유 또는 JWKS 엔드포인트)
4. [ ] Course Service 추출 — 과목 CRUD + 캐싱 적용 지점
5. [ ] Enrollment Service 추출 — 신청/취소/대기열, 정원 카운터 (독립 스케일 아웃 대상, 프로젝트 핵심)
6. [ ] AI Service는 인터페이스/이벤트 계약만 먼저 정의하고 실제 로직은 4단계에서 채움
7. [ ] API Gateway 자체 구현 vs Spring Cloud Gateway 사용 결정 (Architecture Open Question)
8. [ ] 서비스 간 통신 확정: GW↔서비스는 동기 REST, Enrollment→AI는 비동기(Kafka vs Redis Streams, Architecture Open Question)
9. [ ] "도메인 패키지 → 독립 서비스" 추출 과정을 PR/커밋 메시지로 남겨 서비스 분리 스토리를 문서화 (Repo-Strategy 문서 취지)

### 환경 제약 및 대응
- 여러 서비스를 로컬에서 동시에 띄우는 것 자체는 Docker 없이도 포트를 나눠 `bootRun`으로 가능하나, 실제 네트워크 분리·독립 배포 단위 검증은 제한적

---

## 3. 4단계 — AI 봇 탐지 연동

### 목표
매크로/봇 의심 요청을 탐지하고 관리자가 로그를 확인할 수 있게 한다.

### 작업 항목
1. [ ] 탐지 피처 정의: 요청 간격, 반복 패턴, User-Agent 이상치 등 (PRD 6.4)
2. [ ] 알고리즘 확정 (Isolation Forest 등, PRD Open Question)
3. [ ] 구현 방식 결정: Java 내 라이브러리 vs 별도 Python 서비스 — Python 3.14/pip가 로컬에 있어 FastAPI + scikit-learn 조합이 현실적 대안
4. [ ] Enrollment → AI Service 비동기 이벤트 발행 (신청 요청 특징 전달, 신청 처리 자체를 지연시키지 않아야 함)
5. [ ] `bot_detection_logs` 테이블에 대응하는 JPA 엔티티/리포지토리 추가 (스키마는 V1 마이그레이션에 이미 존재, 엔티티는 아직 없음)
6. [ ] `GET /admin/bot-detection/logs` 엔드포인트 구현 (현재 ERD-API-Spec에 "미구현"으로 명시된 상태)
7. [ ] 정탐/오탐률 측정 방법 설계 (PRD 성공 지표)

---

## 4. 5단계 — Docker + CI/CD + AWS 배포

### 작업 항목
1. [ ] 백엔드 앱 자체 Dockerfile 작성 (현재 `docker-compose.yml`은 Postgres/Redis만 포함, 앱 서비스는 없음)
2. [ ] docker-compose에 앱 서비스 추가 (3단계 이후라면 서비스별 compose 파일로 분리)
3. [ ] `.github/workflows/ci.yml` 작성 — 빌드 + 테스트 자동화 (Repo-Strategy 문서에 위치가 이미 계획돼 있음)
4. [ ] GitHub Actions에서 이미지 빌드 → (선택) 컨테이너 레지스트리 푸시
5. [ ] AWS 배포(ECS 또는 EKS) — **실제 계정/자격증명이 필요하고 비용이 발생하는 실제 인프라 생성이므로, 이 문서에는 계획만 남기고 명시적 승인 없이는 진행하지 않는다**

### 환경 제약 및 대응
- Docker 미설치 → 이미지 빌드/실행 로컬 검증 불가, Dockerfile/워크플로 YAML **작성 자체**는 가능
- GitHub remote는 연결되어 있어 push 시 실제 Actions가 돌아간다 → push는 매번 사용자 확인 후 진행
- AWS 배포는 절대 자동 진행하지 않음 (비용 발생 + 실제 리소스 생성)

---

## 5. 진행 순서 제안

기본적으로 PRD/Architecture 로드맵 순서(2→3→4→5)를 따르되, 단계 내부에서 **"코드로 짤 수 있는 것"과 "인프라가 있어야 검증되는 것"을 분리**해서 막히지 않고 계속 진행한다:

- 2단계: Redis 전략 코드 + WebSocket은 먼저 작성 → k6/Redis 설치 여부에 따라 실측 비교는 별도 확인 후 진행
- 3단계: 순수 리팩토링/구조 변경이라 막히는 지점이 거의 없음
- 4단계: Python 활용 여부만 결정되면 바로 진행 가능
- 5단계: Dockerfile/CI YAML 작성까지는 바로 가능, 실제 배포는 보류

각 단계 완료 시 이 문서의 체크박스와 README "진행 상황"을 함께 갱신한다.
