"""EnrollGate 봇 탐지 스코어링 서비스 (4단계, 선택적 강화 경로).

Java 쪽 기본 스코어러(ai-service의 HeuristicBotDetectionScorer)는 규칙 기반이라 항상 동작하지만,
`ai.scorer=isolation-forest`로 설정하면 이 서비스를 호출해 scikit-learn IsolationForest 기반 점수를 대신 쓴다.
이 서비스가 꺼져 있으면 Java 쪽에서 자동으로 휴리스틱으로 대체하므로, 신청 처리 자체에는 영향이 없다.

실행:
    pip install -r requirements.txt
    uvicorn main:app --port 8000

주의: 실제 라벨링된 트래픽 데이터가 없어 "정상 트래픽처럼 보이는" 합성 데이터로 모델을 사전 학습했다.
실제 서비스라면 축적된 신청 로그로 주기적으로 재학습해야 한다.
"""
import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel
from sklearn.ensemble import IsolationForest

app = FastAPI(title="EnrollGate Bot Detection Scorer")

_rng = np.random.default_rng(42)
# 정상 트래픽 중 극소수(약 5%)만 UA 문자열이 드물어 "의심스러움" 신호가 뜨는 상황을 흉내낸다.
# (예전엔 np.random.integers(0,1,...)로 항상 0만 나오는 버그가 있었다 -- 의도는 약간의 노이즈였다.)
_normal_intervals = _rng.uniform(2, 60, 500)
_normal_repeated = _rng.integers(0, 3, 500)
_normal_ua = (_rng.random(500) < 0.05).astype(int)
_returning_visitors = np.column_stack([_normal_intervals, _normal_repeated, _normal_ua])

# 신청 이력이 없는 "첫 신청"은 Java 쪽에서 interval_seconds=-1(sentinel)로 보낸다.
# 위 학습 데이터는 전부 "이전 요청이 있던" 경우(간격 2~60초)만 다루므로, 이 sentinel 값은
# 학습 분포 밖의 극단치가 되어 -- 실제 봇 신호(UA, 반복횟수)와 무관하게 -- 첫 신청을
# 무조건 이상치로 판정하는 오류가 있었다. 정상적인 "첫 신청"도 학습 데이터에 포함시켜 보정한다.
_first_time_count = 150
_first_time_intervals = np.full(_first_time_count, -1.0)
_first_time_repeated = np.ones(_first_time_count, dtype=int)
_first_time_ua = (_rng.random(_first_time_count) < 0.05).astype(int)
_first_time_visitors = np.column_stack([_first_time_intervals, _first_time_repeated, _first_time_ua])

_X_train = np.vstack([_returning_visitors, _first_time_visitors])

_model = IsolationForest(contamination=0.05, random_state=42)
_model.fit(_X_train)


class ScoreRequest(BaseModel):
    interval_seconds: float
    repeated_count_1min: int
    user_agent_suspicious: int


class ScoreResponse(BaseModel):
    suspicion_score: float
    is_anomaly: bool


@app.post("/score", response_model=ScoreResponse)
def score(request: ScoreRequest) -> ScoreResponse:
    features = np.array([[
        request.interval_seconds,
        request.repeated_count_1min,
        request.user_agent_suspicious,
    ]])
    raw_score = float(_model.decision_function(features)[0])  # 클수록 정상, 작을수록(음수) 이상
    is_anomaly = bool(_model.predict(features)[0] == -1)
    # decision_function은 대략 [-0.5, 0.5] 범위라, 부호를 뒤집고 [0, 1]로 클램프해 "의심 점수"로 변환한다.
    suspicion_score = max(0.0, min(1.0, 0.5 - raw_score))
    return ScoreResponse(suspicion_score=suspicion_score, is_anomaly=is_anomaly)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
