package com.bluewhale.pdm.integration.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * ERP 物料同步结果视图
 *
 * @author BlueWhale
 */
@Data
public class PdmIntErpPartResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ERP 物料ID */
    private Long erpItemId;

    /** ERP 物料编码 */
    private String code;

    /** 是否新建（false 表示更新既有物料） */
    private boolean created;

    /** 同步状态：SUCCESS/FAILED */
    private String status;

    /** 失败原因 */
    private String lastError;

    /** 同步日志ID */
    private Long syncLogId;
}
