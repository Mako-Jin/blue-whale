package com.bluewhale.mes.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 工艺路线执行版本快照实体，对应表 {@code mes_md_routing_snapshot}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_md_routing_snapshot")
public class MesRoutingSnapshot extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 工艺路线版本 */
    private String version;

    /** 状态：DRAFT/RELEASED/DISABLED */
    private String status;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 备注 */
    private String remark;
}
