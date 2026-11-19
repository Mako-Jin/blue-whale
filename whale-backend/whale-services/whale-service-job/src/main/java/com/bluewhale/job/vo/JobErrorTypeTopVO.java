package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 失败原因（异常类型）TopN 项。
 *
 * <p>类型从 {@code job_log.error_msg} 的首段提取（调度侧写入格式为 {@code 异常简单类名: 消息}），
 * 无法识别的记录归入 {@code OTHER}，避免把整段错误信息当成类型导致基数爆炸。</p>
 *
 * @author BlueWhale
 */
@Data
public class JobErrorTypeTopVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 异常类型：如 BizException、HttpServerErrorException$BadGateway，无法识别为 OTHER */
    private String errorType;

    /** 出现次数 */
    private long count;

    /** 一条示例错误信息（截断展示） */
    private String sampleMessage;
}
