package com.bluewhale.oa.approval.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 代理规则视图。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalDelegateRuleView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则ID */
    private Long id;

    /** 流程定义编码 */
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
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
