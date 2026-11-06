package com.bluewhale.oa.approval.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 审批操作人上下文。
 *
 * @author BlueWhale
 */
@Data
@AllArgsConstructor
public class ApprovalActor {

    /** 用户ID */
    private Long userId;

    /** 登录账号 */
    private String username;
}
