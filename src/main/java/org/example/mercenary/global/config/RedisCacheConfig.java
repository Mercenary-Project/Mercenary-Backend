package org.example.mercenary.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@EnableCaching
@Configuration
public class RedisCacheConfig {

    public RedisCacheConfig() {
        System.out.println("========== RedisCacheConfig Loaded! ==========");
    }

    @Primary
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // LocalDateTime 직렬화를 위한 ObjectMapper 설정
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // ISO-8601 형식으로 저장
        
        // 역직렬화 시 구체 타입 복원에 필요한 최소 타입 정보만 포함
        // EVERYTHING → NON_FINAL: Long/Integer/Double/String 등 final 클래스는 타입 메타데이터 제외
        // 1,000개 목록 캐시 기준 JSON 크기 약 3~5배 감소 → p95 응답시간 단축
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // 기본 설정: 1시간 뒤 만료, JSON 직렬화
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        // 캐시별 개별 설정
        Map<String, RedisCacheConfiguration> configurations = new HashMap<>();
        
        // 매치 목록은 10분마다 갱신되도록 짧게 설정 (예시)
        configurations.put("matches", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        // 매치 상세 정보는 1시간 유지
        configurations.put("matchDetail", defaultConfig.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configurations)
                .build();
    }
}
