package com.cinta.matchmateserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cinta.matchmateserver.common.ErrorCode;
import com.cinta.matchmateserver.exception.BusinessException;
import com.cinta.matchmateserver.mapper.TagMapper;
import com.cinta.matchmateserver.mapper.UserTagMapper;
import com.cinta.matchmateserver.model.domain.Tag;
import com.cinta.matchmateserver.model.domain.UserTag;
import com.cinta.matchmateserver.model.vo.TagCategoryVO;
import com.cinta.matchmateserver.service.TagService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TagServiceImpl implements TagService {

    private static final int MAX_USER_TAGS = 3;

    private final TagMapper tagMapper;
    private final UserTagMapper userTagMapper;

    public TagServiceImpl(TagMapper tagMapper, UserTagMapper userTagMapper) {
        this.tagMapper = tagMapper;
        this.userTagMapper = userTagMapper;
    }

    @Override
    public List<TagCategoryVO> listCategories() {
        List<Tag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>()
                        .orderByAsc(Tag::getId)
        );

        Map<String, List<String>> groupedTags = new LinkedHashMap<>();
        for (Tag tag : tags) {
            groupedTags.computeIfAbsent(tag.getCategory(), ignored -> new ArrayList<>())
                    .add(tag.getTagName());
        }

        return groupedTags.entrySet().stream()
                .map(entry -> new TagCategoryVO(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public List<String> getUserTagNames(long userId) {
        if (userId <= 0) {
            return List.of();
        }
        return tagMapper.selectTagNamesByUserId(userId);
    }

    @Override
    @Transactional
    public void replaceUserTags(long userId, List<String> tagNames) {
        List<String> normalizedTags = normalizeTags(tagNames);
        if (normalizedTags.size() > MAX_USER_TAGS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "最多选择 3 个标签");
        }

        List<Tag> tags = normalizedTags.isEmpty()
                ? List.of()
                : tagMapper.selectList(
                        new LambdaQueryWrapper<Tag>().in(Tag::getTagName, normalizedTags)
                );
        if (tags.size() != normalizedTags.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "包含不存在或已停用的标签");
        }

        userTagMapper.delete(
                new LambdaQueryWrapper<UserTag>().eq(UserTag::getUserId, userId)
        );
        if (!tags.isEmpty()) {
            Map<String, Long> tagIdsByName = tags.stream()
                    .collect(Collectors.toMap(Tag::getTagName, Tag::getId));
            List<Long> orderedTagIds = normalizedTags.stream()
                    .map(tagIdsByName::get)
                    .toList();
            userTagMapper.insertBatch(userId, orderedTagIds);
        }
    }

    private List<String> normalizeTags(List<String> tagNames) {
        if (tagNames == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "标签列表不能为空");
        }
        return tagNames.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
    }
}
