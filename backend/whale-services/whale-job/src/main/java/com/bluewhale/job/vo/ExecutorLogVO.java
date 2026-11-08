package com.bluewhale.job.vo;

import lombok.Data;

/**
 * 执行器本地日志回捞结果。
 *
 * <p>调度中心只存执行摘要（{@code job_log.result} / {@code error_msg}，落库时截断到 2000 字），
 * 完整日志留在执行器。本对象把「从哪个实例取回了什么」一并带回，便于管理端直接展示。</p>
 *
 * @author BlueWhale
 */
@Data
public class ExecutorLogVO {

    /** 执行流水ID（{@code job_log.id}） */
    private Long logId;

    /** 任务编码 */
    private String jobCode;

    /** 执行器应用名（Nacos 服务名） */
    private String executorApp;

    /** 实际请求到的实例地址（{@code host:port}） */
    private String address;

    /** 是否取到日志 */
    private boolean found;

    /** 未取到时的原因（多副本落到别的实例、实例已重建、任务从未远程触发等） */
    private String note;

    /** 日志正文 */
    private String content;
}
