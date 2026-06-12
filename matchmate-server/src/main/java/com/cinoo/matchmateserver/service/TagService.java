package com.cinoo.matchmateserver.service;

import com.cinoo.matchmateserver.model.vo.TagCategoryVO;

import java.util.List;

public interface TagService {

    /**
     * 获取所有标签分类。
     *
     * @return 标签分类列表。
     */
    List<TagCategoryVO> listCategories();

    /**
     * 获取用户标签名称列表。
     *
     * @param userId 用户ID。
     */
    List<String> getUserTagNames(long userId);

    /**
     * 替换用户标签。
     *
     * @param userId 用户ID。
     */
    void replaceUserTags(long userId, List<String> tagNames);
}
