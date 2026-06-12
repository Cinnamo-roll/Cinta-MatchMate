package com.cinoo.matchmateserver.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class TagCategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String category;
    private List<String> tags;
}
