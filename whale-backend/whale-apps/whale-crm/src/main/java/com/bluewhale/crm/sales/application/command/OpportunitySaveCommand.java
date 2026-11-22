package com.bluewhale.crm.sales.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商机新建 / 编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class OpportunitySaveCommand {

    /** 客户 ID（新建必填） */
    private Long customerId;

    /** 商机名称 */
    @NotBlank(message = "{validation.crm.opportunity.name-required}")
    private String name;

    /** 预计金额 */
    private BigDecimal amount;

    /** 预计关闭日期 */
    private LocalDate expectedCloseDate;

    /** 来源 */
    private String source;

    /** 竞争对手 */
    private String competitor;

    /** 下次行动时间 */
    private LocalDateTime nextActionAt;

    /** 负责人（仅全量数据范围可指定他人） */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 赢单概率（0–100）；为空按阶段默认值 */
    private BigDecimal probability;
}
