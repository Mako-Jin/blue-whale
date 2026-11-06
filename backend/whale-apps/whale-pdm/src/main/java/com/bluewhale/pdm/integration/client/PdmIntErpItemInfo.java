package com.bluewhale.pdm.integration.client;

import java.io.Serializable;

/**
 * ERP 物料只读信息
 *
 * @param id     ERP 物料ID
 * @param code   物料编码
 * @param name   物料名称
 * @param status 状态
 * @param exists ERP 是否存在该物料
 * @author BlueWhale
 */
public record PdmIntErpItemInfo(Long id, String code, String name, String status, boolean exists)
        implements Serializable {
}
