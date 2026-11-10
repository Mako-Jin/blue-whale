package com.bluewhale.pdm.collab.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 研发评审记录
 *
 * <p>设计/样机/转产评审；草稿可修改，关闭必须填写结论且关闭后不可改。
 * 审批实例仅保存快照，审批流本体在 OA/Workflow。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_clb_review")
public class PdmClbReview extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 评审编号（租户内唯一） */
    private String reviewNo;

    /** 类型：DESIGN/PROTOTYPE/TRANSFER */
    private String reviewType;

    /** 评审主题 */
    private String title;

    /** whale-pm 项目ID */
    private Long projectId;

    /** 项目名称快照 */
    private String projectName;

    /** 评审对象类型：DOCUMENT/BOM/ROUTE/PART */
    private String objectType;

    /** 评审对象ID */
    private Long objectId;

    /** 评审对象编号快照 */
    private String objectNo;

    /** 评审对象版本号 */
    private String objectVersionNo;

    /** 参与人，逗号分隔 */
    private String participants;

    /** 结论：PASSED/CONDITIONAL/REJECTED */
    private String conclusion;

    /** 结论说明 */
    private String conclusionComment;

    /** 附件文档ID */
    private Long attachmentDocumentId;

    /** 附件文档版本号 */
    private String attachmentVersionNo;

    /** OA 审批实例ID快照 */
    private Long approvalInstanceId;

    /** 状态：DRAFT/CLOSED */
    private String status;

    /** 创建人 */
    private Long createdBy;

    /** 关闭人 */
    private Long closedBy;

    /** 关闭时间 */
    private LocalDateTime closedAt;
}
