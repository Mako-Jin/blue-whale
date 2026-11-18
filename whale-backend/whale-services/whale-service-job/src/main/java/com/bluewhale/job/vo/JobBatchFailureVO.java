package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量操作中单条失败明细。
 *
 * @author BlueWhale
 */
@Data
public class JobBatchFailureVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 失败的任务ID */
    private Long id;

    /** 失败原因 */
    private String reason;
}
