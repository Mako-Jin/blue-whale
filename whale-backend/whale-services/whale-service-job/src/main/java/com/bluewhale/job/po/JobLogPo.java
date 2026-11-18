package com.bluewhale.job.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志实体，对应表 {@code job_log}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code job_log} 列逐字段对齐；
 * {@code start_time} 在触发执行时显式写入，{@code end_time} 在执行结束时回写。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_log")
public class JobLogPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务ID（逻辑关联 job_info.id） */
    private Long jobId;

    /** 任务名称（冗余，便于日志检索） */
    private String jobName;

    /** 执行器 Bean 名称（冗余） */
    private String handlerBean;

    /** 执行批次ID：一次根触发与其链式下游共享同一 runId */
    private String runId;

    /** 触发类型：CRON 定时 / MANUAL 手动 / CHAIN 依赖链 */
    private String triggerType;

    /** 开始执行时间 */
    private LocalDateTime startTime;

    /** 执行结束时间（执行中为 null） */
    private LocalDateTime endTime;

    /** 执行状态：1 成功 / 0 执行中 / -1 失败 */
    private Integer status;

    /** 本次执行已重试次数（首次执行为 0） */
    private Integer retryCount;

    /** 当前分片序号（从 0 开始；不分片为 0） */
    private Integer shardIndex;

    /**
     * 实际派发到的执行器地址（{@code host:port}）。
     *
     * <p>多副本时执行日志只落在跑过这一次的那台实例，回捞按该地址精确请求；
     * 按参数直连或旧记录为空时回退到注册表寻址。</p>
     */
    private String executorAddress;

    /** 失败原因（异常信息摘要） */
    private String errorMsg;

    /** 执行结果（返回数据摘要） */
    private String result;
}
