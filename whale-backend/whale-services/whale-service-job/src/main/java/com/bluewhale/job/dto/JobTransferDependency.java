package com.bluewhale.job.dto;

import lombok.Data;

/**
 * 导入/导出中的依赖边：{@code from} 为上游任务，{@code to} 为下游任务。
 *
 * @author BlueWhale
 */
@Data
public class JobTransferDependency {

    /** 上游任务引用 */
    private JobTransferRef from;

    /** 下游任务引用 */
    private JobTransferRef to;
}
