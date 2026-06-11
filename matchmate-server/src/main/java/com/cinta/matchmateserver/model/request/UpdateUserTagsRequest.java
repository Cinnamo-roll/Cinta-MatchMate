package com.cinta.matchmateserver.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class UpdateUserTagsRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull
    @Size(max = 3, message = "最多选择 3 个标签")
    private List<@NotBlank(message = "标签不能为空") String> tagList;
}
