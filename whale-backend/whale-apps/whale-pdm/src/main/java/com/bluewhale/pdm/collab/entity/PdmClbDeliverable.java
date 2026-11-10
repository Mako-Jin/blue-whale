package com.bluewhale.pdm.collab.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 项目交付物归档
 *
 * <p>只保存 {@code whale-pm} 项目/任务引用与 PDM 已发布对象版本快照，
 * 不复制项目本体；同项目同对象同版本唯一。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_clb_deliverable")
public class PdmClbDeliverable extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** whale-pm 项目ID */
    private Long projectId;

    /** 项目名称快照 */
    private String projectName;

    /** whale-pm 任务ID */
    private Long workItemId;

    /** 任务名称快照 */
    private String workItemName;

    /** 对象类型：DOCUMENT/BOM/ROUTE */
    private String objectType;

    /** 对象ID */
    private Long objectId;

    /** 对象编号快照 */
    private String objectNo;

    /** 对象名称/标题快照 */
    private String objectTitle;

    /** 已发布对象版本号 */
    private String objectVersionNo;

    /** 交付物类型：DESIGN/DRAWING/REPORT/OTHER */
    private String deliverableType;

    /** 状态：ARCHIVED */
    private String status;

    /** 归档人 */
    private Long archivedBy;

    /** 归档时间 */
    private LocalDateTime archivedAt;

    /** 备注 */
    private String remark;
}
