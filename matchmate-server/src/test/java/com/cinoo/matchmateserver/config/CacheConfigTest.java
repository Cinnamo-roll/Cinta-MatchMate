package com.cinoo.matchmateserver.config;

import com.cinoo.matchmateserver.model.vo.UserVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CacheConfigTest {

    @Test
    void cacheSerializerRestoresUserViewTypes() {
        CacheConfig cacheConfig = new CacheConfig();
        RedisSerializer<Object> serializer = cacheConfig.cacheValueSerializer();
        UserVO user = new UserVO();
        user.setId(1L);
        user.setUsername("测试用户");

        Object restored = serializer.deserialize(
                serializer.serialize(new ArrayList<>(List.of(user)))
        );

        List<?> users = assertInstanceOf(List.class, restored);
        UserVO restoredUser = assertInstanceOf(UserVO.class, users.get(0));
        assertEquals(user.getId(), restoredUser.getId());
        assertEquals(user.getUsername(), restoredUser.getUsername());
    }
}
