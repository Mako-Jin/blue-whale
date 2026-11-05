package com.bluewhale.pdm.integration.client;

import java.io.Serializable;

/**
 * ERP 物料同步结果
 *
 * @param id      ERP 物料ID
 * @param code    物料编码
 * @param created 是否新建
 * @author BlueWhale
 */
public record PdmIntErpItemResult(Long id, String code, boolean created) implements Serializable {
}
