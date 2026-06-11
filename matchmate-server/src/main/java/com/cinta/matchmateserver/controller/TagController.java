package com.cinta.matchmateserver.controller;

import com.cinta.matchmateserver.common.BaseResponse;
import com.cinta.matchmateserver.common.ResultUtils;
import com.cinta.matchmateserver.model.vo.TagCategoryVO;
import com.cinta.matchmateserver.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tag")
@Tag(name = "标签管理", description = "标签目录相关接口")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @Operation(summary = "查询标签分类", description = "按分类返回全部可用标签")
    @GetMapping("/categories")
    public BaseResponse<List<TagCategoryVO>> listCategories() {
        return ResultUtils.success(tagService.listCategories());
    }
}
