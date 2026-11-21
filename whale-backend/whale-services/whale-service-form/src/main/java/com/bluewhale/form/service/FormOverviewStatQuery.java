package com.bluewhale.form.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考功殿概览的统计口径（T5.7）。
 *
 * <p><b>为什么不复用 {@code FormDataServiceImpl#page}：</b>那个方法走 MyBatis-Plus 的
 * 分页插件，为了算出 {@code total} 会把条件行数整表数一遍，还会顺手把当页数据与
 * 物理表的动态列一起捞出来。屏风只要 6 个数（定义数 / 今日提交 / 待审批 / 超时待审 /
 * 待修差异 / 近 7 日趋势），用分页接口问等于每 30 秒拉一次列表。</p>
 *
 * <p><b>口径只有一处。</b>"今天提交了几条"如果屏风和 2D 列表各写一套过滤条件，
 * 迟早出现"城里说 12 条、后台列表 9 条"。所以 {@code deleted = 0 AND status = 1}
 * 这两个条件写死在实现里，由单测钉住；两个字段的含义与 {@code form_data} 列注释一致。</p>
 *
 * <p><b>租户显式传参。</b>总控聚合时线程上下文挂的是"调用者"身份，靠 {@code TenantContext}
 * 取租户等于把别人的数算进这一屏。</p>
 *
 * @author BlueWhale
 */
public interface FormOverviewStatQuery {

    /** 业务状态：审批中（{@code form_data.biz_status = 2}，由 whale-workflow 回调推进） */
    int BIZ_PENDING = 2;

    /**
     * 表单定义数量
     *
     * @param definitions 在册定义数（未逻辑删除）
     * @param enabled     其中状态正常的
     */
    record DefinitionCount(long definitions, long enabled) {
    }

    /**
     * 按天聚合的提交条数
     *
     * @param day     日期
     * @param submits 当日提交条数
     */
    record DaySubmits(LocalDate day, long submits) {
    }

    /**
     * 按表单聚合的提交条数
     *
     * @param formCode 表单编码（分组键）
     * @param formName 表单名称（可能为空：定义已删除或历史数据）
     * @param submits  时间窗内提交条数
     */
    record FormTop(String formCode, String formName, long submits) {
    }

    /**
     * 表单定义数量
     *
     * @param tenantId 租户 ID
     * @return 统计；参数缺失时返回零值
     */
    DefinitionCount definitionCount(Long tenantId);

    /**
     * 时间窗内的提交条数
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 提交条数
     */
    long submittedBetween(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 累计处于指定业务状态的数据条数
     *
     * @param tenantId  租户 ID
     * @param bizStatus 业务状态
     * @return 条数
     */
    long countByBizStatus(Long tenantId, int bizStatus);

    /**
     * 处于指定业务状态、且观察点之前就没再变过的数据条数
     *
     * @param tenantId  租户 ID
     * @param bizStatus 业务状态
     * @param before    观察点
     * @return 条数
     */
    long staleByBizStatus(Long tenantId, int bizStatus, LocalDateTime before);

    /**
     * 待处理的主从对账差异条数
     *
     * @param tenantId 租户 ID
     * @return 条数
     */
    long pendingReconcile(Long tenantId);

    /**
     * 按天聚合的提交条数
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 有数据的日期各一行（缺的日期不补，由调用方补 0）
     */
    List<DaySubmits> dailySubmits(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 时间窗内提交条数最多的表单
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @param limit    取前几名
     * @return 按提交条数降序；无数据时返回空表
     */
    List<FormTop> topForms(Long tenantId, LocalDateTime from, LocalDateTime to, int limit);
}
