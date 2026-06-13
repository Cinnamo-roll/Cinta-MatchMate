package com.cinoo.matchmateserver.tag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("tag")
@Data
public class Tag {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tagName;
    private String category;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;

    @TableLogic
    private Integer isDelete;
}
