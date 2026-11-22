package com.bluewhale.pdm.collab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 研发评审保存请求
 *
 * @author BlueWhale
 */
@Data
public class PdmClbReviewSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 评审类型：DESIGN/PROTOTYPE/TRANSFER */
    @NotBlank(message = "{validation.pdm.pdm-clb-review.review-type-required}")
    private String reviewType;

    /** 评审主题 */
    @NotBlank(message = "{validation.pdm.pdm-clb-review.title-required}")
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

    /** 附件文档ID */
    private Long attachmentDocumentId;

    /** 附件文档版本号 */
    private String attachmentVersionNo;

    /** OA 审批实例ID快照 */
    private Long approvalInstanceId;
}
