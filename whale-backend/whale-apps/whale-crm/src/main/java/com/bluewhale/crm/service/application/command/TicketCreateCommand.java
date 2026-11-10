package com.bluewhale.crm.service.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 建工单命令。
 *
 * @author BlueWhale
 */
@Data
public class TicketCreateCommand {

    /** CRM 客户 ID */
    @NotNull
    private Long customerId;

    /** 联系人 ID */
    private Long contactId;

    /** 联系人姓名 */
    private String contactName;

    /** 合同台账 ID */
    private Long contractId;

    /** 商务订单 ID */
    private Long orderId;

    /** 产品编码 */
    private String productCode;

    /** 序列号 / 设备号 */
    private String serialNo;

    /** 问题分类 */
    private String category;

    /** 优先级 P1/P2/P3 */
    @NotBlank
    private String priority;

    /** 工单标题 */
    @NotBlank
    private String title;

    /** 问题描述 */
    private String description;

    /** 处理人 ID（可空，建单即分派） */
    private Long assigneeId;

    /** 处理人姓名 */
    private String assigneeName;

    /** 负责人 ID（可空，默认当前用户） */
    private Long ownerId;

    /** 负责部门 ID */
    private Long deptId;
}
