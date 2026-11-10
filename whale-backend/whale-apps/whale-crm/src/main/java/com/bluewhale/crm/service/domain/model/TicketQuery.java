package com.bluewhale.crm.service.domain.model;

import lombok.Data;

/**
 * 工单分页查询条件。
 *
 * @author BlueWhale
 */
@Data
public class TicketQuery {

    /** 页码，从 1 开始 */
    private long current = 1;

    /** 每页条数 */
    private long size = 10;

    /** 关键字：工单号 / 标题 / 客户名称 */
    private String keyword;

    /** 状态过滤 */
    private String status;

    /** 优先级过滤 */
    private String priority;

    /** 客户过滤 */
    private Long customerId;

    /** 处理人过滤 */
    private Long assigneeId;

    /** 数据范围用户（非全量数据范围时按负责人或处理人过滤） */
    private Long scopeUserId;
}
