package com.cinoo.matchmateserver.user.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Boolean pendingReview;
    private String message;
}
