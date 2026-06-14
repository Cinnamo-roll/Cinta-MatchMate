package com.cinoo.matchmateserver.user.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationPolicyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer dailyLimit;
    private Long approvedToday;
    private Long pendingCount;
}
