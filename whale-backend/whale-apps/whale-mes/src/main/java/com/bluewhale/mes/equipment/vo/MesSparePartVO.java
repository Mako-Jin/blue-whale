package com.bluewhale.mes.equipment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 备件主档视图
 *
 * @author BlueWhale
 */
@Data
public class MesSparePartVO {

    /** 备件 ID */
    private Long id;

    /** 备件编码 */
    private String partCode;

    /** 备件名称 */
    private String partName;

    /** 规格型号 */
    private String spec;

    /** 计量单位 */
    private String uom;

    /** 安全库存 */
    private BigDecimal safetyStock;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
