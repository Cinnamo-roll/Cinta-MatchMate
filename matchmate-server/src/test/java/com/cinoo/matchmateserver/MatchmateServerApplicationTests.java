package com.cinoo.matchmateserver;

import org.redisson.api.RedissonClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "matchmate.cache.enabled=false",
        "matchmate.cache.warmup.enabled=false",
        "spring.cache.type=simple",
        "spring.session.store-type=none",
        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4"
})
class MatchmateServerApplicationTests {

    @MockitoBean
    private RedissonClient redissonClient;

    @Test
    void contextLoads() {
    }

}
