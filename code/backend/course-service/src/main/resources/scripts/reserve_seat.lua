-- KEYS[1] = 과목별 신청 인원 카운터 키
-- ARGV[1] = 정원(capacity)
--
-- 반환값:
--   1  : 예약 성공 (카운터 증가시킴)
--   0  : 정원 초과
--  -1  : 카운터가 아직 시딩되지 않음 (호출자가 DB 값으로 시딩 후 재시도해야 함)
local exists = redis.call('EXISTS', KEYS[1])
if exists == 0 then
    return -1
end

local count = tonumber(redis.call('GET', KEYS[1]))
local capacity = tonumber(ARGV[1])

if count < capacity then
    redis.call('INCR', KEYS[1])
    return 1
else
    return 0
end
