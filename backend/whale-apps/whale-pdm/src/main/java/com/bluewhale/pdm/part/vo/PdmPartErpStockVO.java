package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 零件 ERP 物料与库存只读视图
 *
 * @author BlueWhale
 */
@Data
public class PdmPartErpStockVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 零件ID */
    private Long partId;

    /** 零件编码 */
    private String partCode;

    /** 查询使用的 ERP 物料编码 */
    private String erpItemCode;

    /** ERP 是否存在该物料 */
    private Boolean itemExists;

    /** ERP 物料名称 */
    private String itemName;

    /** ERP 物料状态 */
    private String itemStatus;

    /** 各仓库数量合计 */
    private BigDecimal totalQuantity;

    /** 仓库明细 */
    private List<PdmPartErpWarehouseVO> warehouses;

    /** ERP 不可用时为 true，此时数量为 0、明细为空 */
    private Boolean degraded;

    /** 降级说明 */
    private String message;
}
