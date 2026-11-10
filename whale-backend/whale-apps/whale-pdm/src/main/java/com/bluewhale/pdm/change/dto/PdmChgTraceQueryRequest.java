package com.bluewhale.pdm.change.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 变更追溯分页查询请求
 *
 * @author BlueWhale
 */
@Data
public class PdmChgTraceQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页 */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;

    /** 业务类型：ECR/ECO/ECN */
    private String bizType;

    /** 业务ID */
    private Long bizId;

    /** 对象类型：DOCUMENT/BOM/PART */
    private String objectType;

    /** 对象ID */
    private Long objectId;

    /** 对象编码 */
    private String objectCode;

    /** 起始时间 */
    private LocalDateTime fromTime;

    /** 结束时间 */
    private LocalDateTime toTime;
}
