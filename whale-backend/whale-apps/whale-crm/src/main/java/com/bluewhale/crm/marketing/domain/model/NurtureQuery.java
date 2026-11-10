package com.bluewhale.crm.marketing.domain.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * 培育任务分页查询条件。
 *
 * @author BlueWhale
 */
@Data
public class NurtureQuery {

    /** 页码，从 1 开始 */
    private long current = 1;

    /** 每页条数 */
    private long size = 10;

    /** 关键字：任务号 / 客户名称 */
    private String keyword;

    /** 状态过滤 */
    private String status;

    /** 客户过滤 */
    private Long customerId;

    /** 执行人过滤 */
    private Long assigneeId;

    /** 计划日期起 */
    private LocalDate planFrom;

    /** 计划日期止 */
    private LocalDate planTo;

    /** 数据范围用户（非全量数据范围时按负责人过滤） */
    private Long scopeUserId;
}
