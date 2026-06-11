package com.cinta.matchmateserver.service;

import com.cinta.matchmateserver.common.ErrorCode;
import com.cinta.matchmateserver.exception.BusinessException;
import com.cinta.matchmateserver.mapper.TagMapper;
import com.cinta.matchmateserver.mapper.UserTagMapper;
import com.cinta.matchmateserver.model.domain.Tag;
import com.cinta.matchmateserver.service.impl.TagServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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

    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagServiceImpl(tagMapper, userTagMapper);
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
    }

    private Tag tag(long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setTagName(name);
        return tag;
    }
}
