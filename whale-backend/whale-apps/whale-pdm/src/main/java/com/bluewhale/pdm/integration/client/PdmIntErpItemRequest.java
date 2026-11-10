package com.bluewhale.pdm.integration.client;

import java.io.Serializable;

/**
 * ERP 物料同步请求
 *
 * @param pdmPartNo PDM 零件编码（ERP 幂等键）
 * @param name      物料名称
 * @param status    状态：ENABLED/DISABLED
 * @param remark    备注（含材质/规格/单位等快照）
 * @author BlueWhale
 */
public record PdmIntErpItemRequest(String pdmPartNo, String name, String status, String remark)
        implements Serializable {
}
