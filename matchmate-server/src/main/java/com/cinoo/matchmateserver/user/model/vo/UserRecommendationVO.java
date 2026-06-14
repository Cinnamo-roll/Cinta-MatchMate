package com.cinoo.matchmateserver.user.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Explainable user recommendation result.
 */
@Data
@AllArgsConstructor
public class UserRecommendationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private UserVO user;
    private int score;
    private String reason;
    private List<String> commonTags;
}
