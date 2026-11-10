package com.bluewhale.mes.collect.dto;

import lombok.Data;

/**
 * 归档策略分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesArchivePolicyQueryRequest {

    /** 策略编码（模糊） */
    private String policyCode;

    /** 归档目标表 */
    private String tableName;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
