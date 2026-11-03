package com.example.demo.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

@Service
public class RedisService {
    private final RedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ACQUIRE_SCRIPT = """
            local current = redis.call('GET', KEYS[1])
            local ttl = tonumber(ARGV[2])
            if not current then
              redis.call('SET', KEYS[1], ARGV[1], 'EX', ttl)
              return 1
            end
            if current == ARGV[1] then
              redis.call('EXPIRE', KEYS[1], ttl)
              return 2
            end
            return 0
            """;

    private static final String RELEASE_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    @Autowired
    public RedisService(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        objectMapper.registerModule(new JavaTimeModule());
    }

    private void configureRedisTemplate() {
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
    }
    public <T> void setEvents(String key, T data, long ttl) {
        redisTemplate.opsForValue().set(key, data, Duration.ofMinutes(ttl));
    }


    public <T> T getEvents(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) return null;
        return objectMapper.convertValue(value, clazz);
    }

    public <T> void setVenues(String key,T data,long ttl){
        redisTemplate.opsForValue().set(key,data,Duration.ofMinutes(ttl));
    }

    public <T> T getSeats(String key, TypeReference<T> typeRef) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) return null;
        return objectMapper.convertValue(value, typeRef);
    }


    public <T> void setSeats(String key,T data,long ttl){
        redisTemplate.opsForValue().set(key,data,Duration.ofMinutes(ttl));
    }

    public <T> T getVenues(String key,Class<T> clazz){
        Object value = redisTemplate.opsForValue().get(key);
        if(value == null)return null;
        return objectMapper.convertValue(value,clazz);
    }

    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }

    public SeatHold lockSeat(ObjectId eventId, String seatNumber, ObjectId userId, long ttlMinutes) {
        if (eventId == null || seatNumber == null || seatNumber.isBlank() || userId == null || ttlMinutes <= 0) {
            return null;
        }
        byte[] key = keyBytes(eventId, seatNumber);
        byte[] owner = ownerBytes(userId);
        if (key == null || owner == null) {
            return null;
        }
        byte[] ttl = Long.toString(ttlMinutes * 60L).getBytes(StandardCharsets.UTF_8);
        long result = eval(ACQUIRE_SCRIPT, key, owner, ttl);
        if (result == 0L) {
            return null;
        }
        return new SeatHold(eventId, seatNumber, userId);
    }

    private void releaseOwned(ObjectId eventId, String seatNumber, ObjectId userId) {
        byte[] key = keyBytes(eventId, seatNumber);
        byte[] owner = ownerBytes(userId);
        if (key == null || owner == null) {
            return;
        }
        eval(RELEASE_SCRIPT, key, owner);
    }

    private String lockKey(ObjectId eventId, String seatNumber) {
        return "seat-lock:" + eventId + ":" + seatNumber.toUpperCase(Locale.ROOT);
    }

    private byte[] keyBytes(ObjectId eventId, String seatNumber) {
        return serialize(redisTemplate.getKeySerializer(), lockKey(eventId, seatNumber));
    }

    private byte[] ownerBytes(ObjectId userId) {
        return serialize(redisTemplate.getValueSerializer(), userId.toHexString());
    }

    private byte[] serialize(RedisSerializer serializer, String value) {
        if (serializer == null || value == null) {
            return null;
        }
        return serializer.serialize(value);
    }

    private long eval(String script, byte[]... keysAndArgs) {
        Object raw = redisTemplate.execute((RedisCallback<Object>) connection -> connection.scriptingCommands().eval(
                script.getBytes(StandardCharsets.UTF_8),
                ReturnType.INTEGER,
                1,
                keysAndArgs));
        if (raw instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    public final class SeatHold {
        private final ObjectId eventId;
        private final String seatNumber;
        private final ObjectId userId;

        private SeatHold(ObjectId eventId, String seatNumber, ObjectId userId) {
            this.eventId = eventId;
            this.seatNumber = seatNumber;
            this.userId = userId;
        }

        public String seatNumber() {
            return seatNumber;
        }

        public void release() {
            releaseOwned(eventId, seatNumber, userId);
        }
    }
}
