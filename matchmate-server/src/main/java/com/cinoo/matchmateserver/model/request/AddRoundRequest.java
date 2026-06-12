package com.cinoo.matchmateserver.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新增牌局请求。
 */
@Data
public class AddRoundRequest {

    @NotNull(message = "分数列表不能为空")
    @Size(min = 2, max = 8, message = "牌局至少需要2人参与，最多8人")
    private List<ScoreEntry> scores;

    @Data
    public static class ScoreEntry {
        @NotNull(message = "用户ID不能为空")
        private Long userId;

        @NotNull(message = "积分不能为空")
        private Integer score;
    }
}
