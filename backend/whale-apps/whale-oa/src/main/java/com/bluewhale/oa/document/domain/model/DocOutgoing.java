package com.bluewhale.oa.document.domain.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发文单据。
 *
 * @author BlueWhale
 */
@Data
public class DocOutgoing {

    /** 主键ID */
    private Long id;

    /** 文号 */
    private String docNo;

    /** 公文标题 */
    private String title;

    /** 文种 */
    private String docType;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    private Integer securityLevel;

    /** 紧急度：1 平件 / 2 急件 / 3 特急 */
    private Integer urgency;

    /** 模板ID */
    private Long templateId;

    /** 模板名称快照 */
    private String templateName;

    /** 拟稿人用户ID */
    private Long drafterId;

    /** 拟稿人姓名快照 */
    private String drafterName;

    /** 拟稿部门ID */
    private Long deptId;

    /** 主送单位/人员 */
    private String mainRecipients;

    /** 抄送单位/人员 */
    private String copyRecipients;

    /** 正文 */
    private String content;

    /** 签发人 */
    private String signer;

    /** 签发日期 */
    private LocalDate signDate;

    /** 下发时间 */
    private LocalDateTime issuedAt;

    /** 状态 */
    private String status;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 档案条目ID */
    private String archiveItemId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
