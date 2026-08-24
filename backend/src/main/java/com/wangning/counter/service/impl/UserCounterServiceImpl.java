package com.wangning.counter.service.impl;

import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.counter.schema.CounterKeys;
import com.wangning.counter.schema.UserCounterMetric;
import com.wangning.counter.service.UserCounterService;
import com.wangning.counter.service.UserCounters;
import com.wangning.counter.service.CounterService;
import com.wangning.knowpost.mapper.KnowPostMapper;
import com.wangning.relation.mapper.RelationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 基于固定五段 Redis SDS 的用户计数服务实现。
 *
 * <p>所有字段更新通过 Lua 在 Redis 内执行，计数下限始终为零。SDS 缺失或长度异常时会在
 * 下一次读写中恢复为合法的五段结构。</p>
 */
@Service
@RequiredArgsConstructor
public class UserCounterServiceImpl implements UserCounterService {

    private static final int FIELD_SIZE = 4;
    private static final int FIELD_COUNT = 5;
    private static final RedisScript<Long> INCREMENT_SCRIPT = RedisScript.of("""
            local fieldSize = tonumber(ARGV[1])
            local fieldCount = tonumber(ARGV[2])
            local field = tonumber(ARGV[3])
            local delta = tonumber(ARGV[4])
            local value = redis.call('GET', KEYS[1])
            if not value or string.len(value) ~= fieldSize * fieldCount then
                value = string.rep(string.char(0), fieldSize * fieldCount)
            end

            local function read32be(source, offset)
                local b1, b2, b3, b4 = string.byte(source, offset + 1, offset + 4)
                return ((b1 or 0) * 16777216) + ((b2 or 0) * 65536) + ((b3 or 0) * 256) + (b4 or 0)
            end

            local function write32be(number)
                local bytes = {}
                for index = 4, 1, -1 do
                    bytes[index] = number % 256
                    number = math.floor(number / 256)
                end
                return string.char(unpack(bytes))
            end

            local offset = field * fieldSize
            local nextValue = read32be(value, offset) + delta
            if nextValue < 0 then
                nextValue = 0
            end
            value = string.sub(value, 1, offset)
                    .. write32be(nextValue)
                    .. string.sub(value, offset + fieldSize + 1)
            redis.call('SET', KEYS[1], value)
            return nextValue
            """, Long.class);
    private static final RedisScript<List> READ_SCRIPT = RedisScript.of("""
            local fieldSize = tonumber(ARGV[1])
            local fieldCount = tonumber(ARGV[2])
            local value = redis.call('GET', KEYS[1])
            if not value or string.len(value) ~= fieldSize * fieldCount then
                value = string.rep(string.char(0), fieldSize * fieldCount)
            end

            local function read32be(source, offset)
                local b1, b2, b3, b4 = string.byte(source, offset + 1, offset + 4)
                return ((b1 or 0) * 16777216) + ((b2 or 0) * 65536) + ((b3 or 0) * 256) + (b4 or 0)
            end

            local result = {}
            for field = 0, fieldCount - 1 do
                result[#result + 1] = read32be(value, field * fieldSize)
            end
            return result
            """, List.class);
    private static final RedisScript<Long> VALIDATE_STRUCTURE_SCRIPT = RedisScript.of("""
            local value = redis.call('GET', KEYS[1])
            if redis.call('EXISTS', KEYS[2]) == 1
                    and value and string.len(value) == tonumber(ARGV[1]) then
                return 1
            end
            return 0
            """, Long.class);
    private static final RedisScript<Long> REBUILD_SCRIPT = RedisScript.of("""
            local function write32be(number)
                if number < 0 then number = 0 end
                if number > 4294967295 then number = 4294967295 end
                local bytes = {}
                for index = 4, 1, -1 do
                    bytes[index] = number % 256
                    number = math.floor(number / 256)
                end
                return string.char(unpack(bytes))
            end
            local value = ''
            for index = 1, 5 do
                value = value .. write32be(tonumber(ARGV[index]))
            end
            redis.call('SET', KEYS[1], value)
            redis.call('SET', KEYS[2], '1')
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RelationMapper relationMapper;
    private final KnowPostMapper knowPostMapper;
    private final CounterService counterService;

    /** {@inheritDoc} */
    @Override
    public void incrementFollowings(long userId, int delta) {
        increment(userId, UserCounterMetric.FOLLOWINGS, delta);
    }

    /** {@inheritDoc} */
    @Override
    public void incrementFollowers(long userId, int delta) {
        increment(userId, UserCounterMetric.FOLLOWERS, delta);
    }

    /** {@inheritDoc} */
    @Override
    public void incrementPosts(long userId, int delta) {
        increment(userId, UserCounterMetric.POSTS, delta);
    }

    /** {@inheritDoc} */
    @Override
    public void incrementLikesReceived(long userId, int delta) {
        increment(userId, UserCounterMetric.LIKES_RECEIVED, delta);
    }

    /** {@inheritDoc} */
    @Override
    public void incrementFavsReceived(long userId, int delta) {
        increment(userId, UserCounterMetric.FAVS_RECEIVED, delta);
    }

    /** {@inheritDoc} */
    @Override
    public UserCounters getCounters(long userId) {
        validateUserId(userId);
        List<?> values = redisTemplate.execute(
                READ_SCRIPT,
                List.of(CounterKeys.userSdsKey(userId)),
                String.valueOf(FIELD_SIZE),
                String.valueOf(FIELD_COUNT)
        );
        return new UserCounters(
                readValue(values, UserCounterMetric.FOLLOWINGS),
                readValue(values, UserCounterMetric.FOLLOWERS),
                readValue(values, UserCounterMetric.POSTS),
                readValue(values, UserCounterMetric.LIKES_RECEIVED),
                readValue(values, UserCounterMetric.FAVS_RECEIVED)
        );
    }

    /** {@inheritDoc} */
    @Override
    public boolean isInitialized(long userId) {
        validateUserId(userId);
        Long valid = redisTemplate.execute(
                VALIDATE_STRUCTURE_SCRIPT,
                List.of(CounterKeys.userSdsKey(userId), CounterKeys.userCounterInitializedKey(userId)),
                String.valueOf(FIELD_SIZE * FIELD_COUNT)
        );
        return valid != null && valid == 1L;
    }

    /** {@inheritDoc} */
    @Override
    public UserCounters getOrRebuildCounters(long userId) {
        validateUserId(userId);
        if (!isInitialized(userId)) {
            return rebuildCounters(userId);
        }
        return getCounters(userId);
    }

    /** {@inheritDoc} */
    @Override
    public UserCounters rebuildCounters(long userId) {
        validateUserId(userId);
        long followings = relationMapper.countFollowings(userId);
        long followers = relationMapper.countFollowers(userId);
        List<Long> postIds = knowPostMapper.listPublishedIdsByCreator(userId);
        List<Long> safePostIds = postIds == null ? List.of() : postIds;
        long likesReceived = 0L;
        long favsReceived = 0L;
        for (Long postId : safePostIds) {
            if (postId == null) {
                continue;
            }
            Map<String, Long> counts = counterService.getCounts(
                    "knowpost",
                    String.valueOf(postId),
                    List.of("like", "fav")
            );
            likesReceived += counts.getOrDefault("like", 0L);
            favsReceived += counts.getOrDefault("fav", 0L);
        }
        UserCounters counters = new UserCounters(
                followings,
                followers,
                safePostIds.size(),
                likesReceived,
                favsReceived
        );
        redisTemplate.execute(
                REBUILD_SCRIPT,
                List.of(CounterKeys.userSdsKey(userId), CounterKeys.userCounterInitializedKey(userId)),
                String.valueOf(counters.followings()),
                String.valueOf(counters.followers()),
                String.valueOf(counters.posts()),
                String.valueOf(counters.likesReceived()),
                String.valueOf(counters.favsReceived())
        );
        return counters;
    }

    private void increment(long userId, UserCounterMetric metric, int delta) {
        validateUserId(userId);
        if (delta == 0) {
            return;
        }
        redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(CounterKeys.userSdsKey(userId)),
                String.valueOf(FIELD_SIZE),
                String.valueOf(FIELD_COUNT),
                String.valueOf(metric.index()),
                String.valueOf(delta)
        );
    }

    private long readValue(List<?> values, UserCounterMetric metric) {
        if (values == null || values.size() <= metric.index()) {
            return 0L;
        }
        return ((Number) values.get(metric.index())).longValue();
    }

    private void validateUserId(long userId) {
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户 ID 必须为正整数");
        }
    }
}
