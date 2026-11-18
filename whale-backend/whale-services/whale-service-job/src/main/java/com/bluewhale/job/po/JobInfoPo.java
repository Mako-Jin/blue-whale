package com.bluewhale.job.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务信息实体，对应表 {@code job_info}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code job_info} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_info")
public class JobInfoPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属租户ID（调度执行时绑定租户上下文） */
    private Long tenantId;

    /** 任务名称 */
    private String jobName;

    /** 任务分组，便于分类管理 */
    private String jobGroup;

    /** 执行器应用名（Nacos 服务名），HTTP 型任务据此寻址 */
    private String executorApp;

    /** 任务编码（对应 @ScheduledJob.code），为空时按执行器参数直连 */
    private String jobCode;

    /** Cron 执行表达式，如 {@code 0 0/5 * * * ?} */
    private String cronExpression;

    /** 执行器 Bean 名称（Spring 容器中的处理器） */
    private String handlerBean;

    /** 执行器参数（任务执行时透传） */
    private String handlerParam;

    /** 错过执行策略：SMART / FIRE_ONCE / DO_NOTHING */
    private String misfirePolicy;

    /** 失败重试次数上限：0 表示不重试 */
    private Integer maxRetry;

    /** 重试间隔秒数 */
    private Integer retryIntervalSeconds;

    /**
     * 分片总数：1 表示不分片，分片间串行执行。
     *
     * <p>{@code -1} 为动态分片，执行时按可派发实例数决定分片数。</p>
     */
    private Integer shardTotal;

    /** 任务状态：1 运行中 / 0 已停止 */
    private Integer status;

    /** 备注 */
    private String remark;
}
