package com.bluewhale.pdm.process.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 工艺路线
 *
 * <p>按零件/产品建路线，版本号形如 {@code V1.0}；发布后路线、工序、工装与 SOP
 * 均不可再改，只能新建版本。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_prc_route")
public class PdmPrcRoute extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 工艺路线编号（租户内与版本组合唯一） */
    private String routeNo;

    /** 零件/产品ID */
    private Long partId;

    /** 零件编码快照 */
    private String partCode;

    /** 零件名称快照 */
    private String partName;

    /** 版本号，如 V1.0 */
    private String versionNo;

    /** 版本排序值 */
    private Integer versionSeq;

    /** 状态：DRAFT/RELEASED/OBSOLETE */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private Long createdBy;

    /** 发布人 */
    private Long releasedBy;

    /** 发布时间 */
    private LocalDateTime releasedAt;
}
