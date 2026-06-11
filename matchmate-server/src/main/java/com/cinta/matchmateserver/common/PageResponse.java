package com.cinta.matchmateserver.common;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页响应。
 *
 * @param <T> 记录类型
 */
@Data
@AllArgsConstructor
public class PageResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;
    private long pageNum;
    private long pageSize;
    private List<T> records;
}
