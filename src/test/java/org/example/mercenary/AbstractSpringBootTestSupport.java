package org.example.mercenary;

import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractSpringBootTestSupport {

    @MockBean
    protected RedissonClient redissonClient;

    @MockBean
    protected StringRedisTemplate stringRedisTemplate;
}
