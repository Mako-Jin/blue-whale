package com.bluewhale.message.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 失败原因统计视图。
 *
 * @author BlueWhale
 */
@Data
public class FailureStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道 */
    private String channel;

    /** 失败原因摘要（可能为空） */
    private String errorMessage;

    /** 命中条数 */
    private long count;
}
