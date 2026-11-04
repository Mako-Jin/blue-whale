package com.bluewhale.mes.collect.vo;

import lombok.Data;

/**
 * 样本上报结果视图
 *
 * @author BlueWhale
 */
@Data
public class MesMetricIngestResultVO {

    /** 采集点编码 */
    private String pointCode;

    /** 接收条数 */
    private Integer accepted;

    /** 幂等跳过条数 */
    private Integer skipped;

    /** 结果说明 */
    private String message;
}
