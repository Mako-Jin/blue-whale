package com.bluewhale.pdm.collab.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 研发评审视图
 *
 * @author BlueWhale
 */
@Data
public class PdmClbReviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 评审ID */
    private Long id;

    /** 评审编号 */
    private String reviewNo;

    /** 评审类型 */
    private String reviewType;

    /** 评审主题 */
    private String title;

    /** 项目ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** 评审对象类型 */
    private String objectType;

    /** 评审对象ID */
    private Long objectId;

    /** 评审对象编号 */
    private String objectNo;

    /** 评审对象版本号 */
    private String objectVersionNo;

    /** 参与人 */
    private String participants;

    /** 结论 */
    private String conclusion;

    /** 结论说明 */
    private String conclusionComment;

    /** 附件文档ID */
    private Long attachmentDocumentId;

    /** 附件文档版本号 */
    private String attachmentVersionNo;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 状态 */
    private String status;

    /** 创建人 */
    private Long createdBy;

    /** 关闭人 */
    private Long closedBy;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
