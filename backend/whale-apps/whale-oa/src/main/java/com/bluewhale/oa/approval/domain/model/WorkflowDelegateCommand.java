package com.bluewhale.oa.approval.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workflow 代理规则保存命令。
 *
 * @author BlueWhale
 */
@Data
public class WorkflowDelegateCommand {

    /** 流程编码 */
    private String definitionCode;

    /** 委托人账号 */
    private String fromAccount;

    /** 代理人账号 */
    private String toAccount;

    /** 生效开始时间 */
    private LocalDateTime startTime;

    /** 生效结束时间 */
    private LocalDateTime endTime;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注 */
    private String remark;
}
