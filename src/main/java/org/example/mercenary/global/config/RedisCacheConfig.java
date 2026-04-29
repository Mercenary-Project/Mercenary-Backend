package org.example.mercenary.global.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.mercenary.domain.match.dto.MatchDetailResponseDto;
import org.example.mercenary.domain.match.dto.MatchSearchResponseDto;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
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
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // activateDefaultTyping 제거: @class 메타데이터 없이 순수 JSON으로 저장
        // 대신 캐시별 Jackson2JsonRedisSerializer로 타입 명시 → 직렬화 크기 감소 → Hit 속도 향상

        StringRedisSerializer keySerializer = new StringRedisSerializer();

        // "matches" 캐시: List<MatchSearchResponseDto> 타입 명시
        JavaType matchListType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, MatchSearchResponseDto.class);
        Jackson2JsonRedisSerializer<?> matchesSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, matchListType);
        RedisCacheConfiguration matchesConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(matchesSerializer));

        // "matchDetail" 캐시: MatchDetailResponseDto 타입 명시
        Jackson2JsonRedisSerializer<MatchDetailResponseDto> detailSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, MatchDetailResponseDto.class);
        RedisCacheConfiguration detailConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(detailSerializer));

        Map<String, RedisCacheConfiguration> configurations = new HashMap<>();
        configurations.put("matches", matchesConfig);
        configurations.put("matchDetail", detailConfig);

        // 기본 설정 (향후 캐시 추가 대비)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configurations)
                .build();
    }
}
