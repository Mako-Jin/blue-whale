package com.bluewhale.job.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务失败告警记录实体，对应表 {@code job_alert}。
 *
 * <p>每次任务失败终态都会写入一条记录（无论是否实际外发），便于审计与重放；
 * 公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_alert")
public class JobAlert extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务ID（逻辑关联 job_info.id） */
    private Long jobId;

    /** 任务名称（冗余） */
    private String jobName;

    /** 关联执行日志ID（逻辑关联 job_log.id） */
    private Long logId;

    /** 执行批次ID（与 job_log.run_id 一致） */
    private String runId;

    /** 告警级别，默认 ERROR */
    private String level;

    /** 告警通道：LOG 仅本地日志 / WEBHOOK 外发 */
    private String channel;

    /** 投递状态：1 成功 / -1 失败 / 0 待发 */
    private Integer status;

    /** 告警摘要 */
    private String message;

    /** 失败原因（异常信息摘要） */
    private String errorMsg;
}
