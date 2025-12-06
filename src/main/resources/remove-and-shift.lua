-- KEYS[1] = queue key, e.g. "queue:doctor:123"
-- ARGV[1] = patientId to remove, e.g. "456"

local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
if not score then
    return 0  -- not found
end

-- Удаляем пациента
redis.call('ZREM', KEYS[1], ARGV[1])

-- Сдвигаем всех с позицией > score: уменьшаем score на 1
local members = redis.call('ZRANGEBYSCORE', KEYS[1], '(' .. score, '+inf')
for i, member in ipairs(members) do
    redis.call('ZINCRBY', KEYS[1], -1, member)
end

return #members + 1

