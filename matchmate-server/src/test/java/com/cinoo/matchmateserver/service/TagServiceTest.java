package com.cinoo.matchmateserver.service;

import com.cinoo.matchmateserver.cache.CacheInvalidationService;
import com.cinoo.matchmateserver.cache.DistributedCacheService;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.mapper.TagMapper;
import com.cinoo.matchmateserver.mapper.UserTagMapper;
import com.cinoo.matchmateserver.model.domain.Tag;
import com.cinoo.matchmateserver.service.impl.TagServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagMapper tagMapper;

    @Mock
    private UserTagMapper userTagMapper;

    @Mock
    private DistributedCacheService cacheService;

    @Mock
    private CacheInvalidationService cacheInvalidationService;

    private TagService tagService;

    @BeforeEach
    void setUp() {
        lenient().when(cacheService.get(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> loader = invocation.getArgument(2);
                    return loader.get();
                });
        tagService = new TagServiceImpl(
                tagMapper,
                userTagMapper,
                cacheService,
                cacheInvalidationService
        );
    }

    @Test
    void replaceUserTagsRejectsMoreThanThreeTags() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> tagService.replaceUserTags(
                        1L,
                        List.of("跑步", "摄影", "咖啡", "电影")
                )
        );

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(tagMapper, userTagMapper);
    }

    @Test
    void replaceUserTagsRejectsUnknownTag() {
        Tag running = tag(1L, "跑步");
        when(tagMapper.selectList(any())).thenReturn(List.of(running));

        assertThrows(
                BusinessException.class,
                () -> tagService.replaceUserTags(1L, List.of("跑步", "不存在"))
        );

        verifyNoInteractions(userTagMapper);
    }

    @Test
    void replaceUserTagsDeletesOldLinksAndInsertsNewLinks() {
        when(tagMapper.selectList(any())).thenReturn(
                List.of(tag(1L, "跑步"), tag(2L, "摄影"))
        );

        tagService.replaceUserTags(10L, List.of("跑步", "摄影"));

        verify(userTagMapper).delete(any());
        verify(userTagMapper).insertBatch(10L, List.of(1L, 2L));
        verify(cacheInvalidationService).userTagsChanged(10L);
    }

    private Tag tag(long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setTagName(name);
        return tag;
    }
}
