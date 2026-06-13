package com.cinoo.matchmateserver.tag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cinoo.matchmateserver.infrastructure.cache.CacheInvalidationService;
import com.cinoo.matchmateserver.infrastructure.cache.CacheKeys;
import com.cinoo.matchmateserver.infrastructure.cache.CacheNames;
import com.cinoo.matchmateserver.infrastructure.cache.DistributedCacheService;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.tag.mapper.TagMapper;
import com.cinoo.matchmateserver.tag.mapper.UserTagMapper;
import com.cinoo.matchmateserver.tag.model.entity.Tag;
import com.cinoo.matchmateserver.tag.model.entity.UserTag;
import com.cinoo.matchmateserver.tag.model.vo.TagCategoryVO;
import com.cinoo.matchmateserver.tag.service.TagService;
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
    private final DistributedCacheService cacheService;
    private final CacheInvalidationService cacheInvalidationService;

    public TagServiceImpl(
            TagMapper tagMapper,
            UserTagMapper userTagMapper,
            DistributedCacheService cacheService,
            CacheInvalidationService cacheInvalidationService) {
        this.tagMapper = tagMapper;
        this.userTagMapper = userTagMapper;
        this.cacheService = cacheService;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Override
    public List<TagCategoryVO> listCategories() {
        return cacheService.get(
                CacheNames.TAG_CATEGORIES,
                CacheKeys.ALL,
                this::loadCategories
        );
    }

    public List<TagCategoryVO> refreshCategoriesCache() {
        return cacheService.refresh(
                CacheNames.TAG_CATEGORIES,
                CacheKeys.ALL,
                this::loadCategories
        );
    }

    private List<TagCategoryVO> loadCategories() {
        List<Tag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>()
                        .orderByAsc(Tag::getId)
        );

        Map<String, List<String>> groupedTags = new LinkedHashMap<>();
        for (Tag tag : tags) {
            groupedTags.computeIfAbsent(tag.getCategory(), ignored -> new ArrayList<>())
                    .add(tag.getTagName());
        }

        return new ArrayList<>(groupedTags.entrySet().stream()
                .map(entry -> new TagCategoryVO(entry.getKey(), entry.getValue()))
                .toList());
    }

    @Override
    public List<String> getUserTagNames(long userId) {
        if (userId <= 0) {
            return List.of();
        }
        return cacheService.get(
                CacheNames.USER_TAGS,
                CacheKeys.user(userId),
                () -> new ArrayList<>(tagMapper.selectTagNamesByUserId(userId))
        );
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
        cacheInvalidationService.userTagsChanged(userId);
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
