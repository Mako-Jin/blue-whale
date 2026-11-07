package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 行动项候选数据（由只读查询端口返回，规则引擎据此生成行动项）。
 *
 * @author BlueWhale
 */
@Data
public class ActionCandidate {

    /** 关联对象类型 */
    private ActionType actionType;

    /** 关联对象 ID */
    private Long refId;

    /** 客户 ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 对象名称（客户 / 商机 / 合同 / 工单） */
    private String title;

    /** 指标值（缺口金额、逾期金额、满意度评分等） */
    private BigDecimal metricValue;

    /** 逾期 / 未跟进天数 */
    private Integer overdueDays;

    /** 建议完成日期 */
    private LocalDate dueDate;

    /** 建议优先级 */
    private String priority;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;
}
