package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 导入过程中单条失败明细。
 *
 * @author BlueWhale
 */
@Data
public class JobImportFailureVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 失败对象标识：优先 {@code executorApp:jobCode}，否则任务名称或 {@code jobId=N} */
    private String target;

    /** 失败原因 */
    private String reason;
}
