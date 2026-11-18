package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务告警记录视图对象（字段与 {@code job_alert} 表一致）。
 *
 * @author BlueWhale
 */
@Data
public class JobAlertVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 任务ID */
    private Long jobId;

    /** 任务名称（冗余） */
    private String jobName;

    /** 关联执行日志ID */
    private Long logId;

    /** 执行批次ID */
    private String runId;

    /** 告警级别 */
    private String level;

    /** 告警通道：LOG 仅本地日志，多个外发通道以逗号拼接（如 DINGTALK,FEISHU） */
    private String channel;

    /** 是否恢复通知：级别为 INFO 的告警即「任务已恢复」 */
    private Boolean recovered;

    /** 投递状态：1 成功 / -1 失败 / 0 待发 */
    private Integer status;

    /** 告警摘要 */
    private String message;

    /** 失败原因 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createTime;
}
