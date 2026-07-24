#!/usr/bin/env bash
# k6 부하테스트에서 사용할 고정 관리자 계정을 준비한다 (없으면 생성 후 ADMIN으로 승격, 있으면 그대로 둠).
# 회원가입 API는 항상 STUDENT로만 계정을 만들기 때문에, 관리자 승격은 DB에 직접 접근해서 처리한다.
#
# 사용법: PGPASSWORD 환경변수나 기본값(enrollgate)을 사용하는 로컬 포터블 Postgres를 전제로 한다.
#   ./seed-perf-admin.sh <psql경로> <앱 base url>
set -euo pipefail

PSQL_BIN="${1:-psql}"
BASE_URL="${2:-http://localhost:8080/api/v1}"
ADMIN_EMAIL="perf-admin@enrollgate.com"
ADMIN_PASSWORD="perfpass123"

curl -s -X POST "$BASE_URL/auth/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\",\"name\":\"성능테스트관리자\",\"studentNumber\":\"PERF-ADMIN-0001\"}" \
  > /dev/null || true

PGPASSWORD="${PGPASSWORD:-enrollgate}" "$PSQL_BIN" -h localhost -p 5432 -U enrollgate -d enrollgate \
  -c "UPDATE users SET role='ADMIN' WHERE email='$ADMIN_EMAIL';"

echo "perf-admin ready: $ADMIN_EMAIL"
