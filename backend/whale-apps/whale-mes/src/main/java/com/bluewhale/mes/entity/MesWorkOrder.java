package com.bluewhale.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 生产工单实体，对应表 {@code mes_work_order}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code mes_work_order} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_work_order")
public class MesWorkOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 工单编码（租户内唯一） */
    private String code;

    /** 工单名称 */
    private String name;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;
}
