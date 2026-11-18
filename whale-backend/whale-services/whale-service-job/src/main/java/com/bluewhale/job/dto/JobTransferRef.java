package com.bluewhale.job.dto;

import lombok.Data;

/**
 * 导入/导出中的任务引用：用业务标识而非数据库 ID 定位任务。
 *
 * <p>跨环境主键必然不同，因此依赖边以 {@code executorApp + jobCode} 为主键，
 * {@code jobName} 用于人工核对与 {@code jobCode} 缺失时的兜底匹配。</p>
 *
 * @author BlueWhale
 */
@Data
public class JobTransferRef {

    /** 执行器应用名 */
    private String executorApp;

    /** 任务编码，可能为空 */
    private String jobCode;

    /** 任务名称，用于展示与兜底匹配 */
    private String jobName;
}
