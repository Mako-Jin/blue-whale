package com.bluewhale.pdm.process.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PDM 工艺工装夹具
 *
 * <p>可关联到具体工序，也可作为路线级工装（{@code stepId} 为空）。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_prc_tooling")
public class PdmPrcTooling extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 工艺路线ID */
    private Long routeId;

    /** 工序ID（为空表示路线级工装） */
    private Long stepId;

    /** 类型：TOOL/FIXTURE/MOLD/GAUGE */
    private String toolingType;

    /** 工装编码 */
    private String toolingCode;

    /** 规格 */
    private String spec;

    /** 数量 */
    private Integer quantity;

    /** 备注 */
    private String remark;
}
