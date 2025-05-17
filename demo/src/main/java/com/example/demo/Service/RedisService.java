package com.example.demo.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisService {
    @Autowired
    private RedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisService() {
        objectMapper.registerModule(new JavaTimeModule());  // Registering the JavaTimeModule
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


    public <T> T getVenues(String key,Class<T> clazz){
        Object value = redisTemplate.opsForValue().get(key);
        if(value == null)return null;
        return objectMapper.convertValue(value,clazz);
    }

    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }

    public boolean lockSeat(ObjectId eventId, String seatNumber, ObjectId userId, long ttlMinutes) {
        String lockKey = "seat-lock:" + eventId + ":" + seatNumber;
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, userId.toHexString(), Duration.ofMinutes(ttlMinutes));
        return Boolean.TRUE.equals(isLocked); // safely handles null
    }


    public void unlockSeat(ObjectId eventId, String seatNumber) {
        String lockKey = "seat-lock:" + eventId + ":" + seatNumber;
        redisTemplate.delete(lockKey);  // Remove the lock from Redis
    }
}
