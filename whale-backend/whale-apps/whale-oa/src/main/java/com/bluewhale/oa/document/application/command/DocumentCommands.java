package com.bluewhale.oa.document.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 公文模块写命令集合。
 *
 * @author BlueWhale
 */
@Data
public class DocumentCommands {

    /**
     * 模板保存命令。
     */
    @Data
    public static class TemplateSave {

        /** 模板编码 */
        @NotBlank(message = "{validation.oa.template-save.code-required}")
        @Size(max = 64, message = "{validation.oa.template-save.code-size}")
        private String code;

        /** 模板名称 */
        @NotBlank(message = "{validation.oa.template-save.name-required}")
        @Size(max = 128, message = "{validation.oa.template-save.name-size}")
        private String name;

        /** 适用范围 */
        @NotBlank(message = "{validation.oa.template-save.doc-type-required}")
        private String docType;

        /** 版本号 */
        @Min(value = 1, message = "{validation.oa.template-save.version-min}")
        private Integer version;

        /** 状态 */
        private String status;

        /** 红头文字 */
        @Size(max = 512, message = "{validation.oa.template-save.header-text-size}")
        private String headerText;

        /** 版记文字 */
        @Size(max = 512, message = "{validation.oa.template-save.footer-text-size}")
        private String footerText;

        /** 正文模板 */
        private String bodyTemplate;

        /** 备注 */
        @Size(max = 255, message = "{validation.oa.template-save.remark-size}")
        private String remark;
    }

    /**
     * 文号规则保存命令。
     */
    @Data
    public static class NoRuleSave {

        /** 规则编码 */
        @NotBlank(message = "{validation.oa.no-rule-save.code-required}")
        @Size(max = 64, message = "{validation.oa.no-rule-save.code-size}")
        private String code;

        /** 规则名称 */
        @NotBlank(message = "{validation.oa.no-rule-save.name-required}")
        @Size(max = 128, message = "{validation.oa.no-rule-save.name-size}")
        private String name;

        /** 适用范围 */
        @NotBlank(message = "{validation.oa.no-rule-save.doc-type-required}")
        private String docType;

        /** 文号前缀 */
        @Size(max = 64, message = "{validation.oa.no-rule-save.prefix-size}")
        private String prefix;

        /** 期间格式 */
        private String periodFormat;

        /** 流水位数 */
        @Min(value = 3, message = "{validation.oa.no-rule-save.serial-length-min}")
        @Max(value = 6, message = "{validation.oa.no-rule-save.serial-length-max}")
        private Integer serialLength;

        /** 分隔符 */
        @Size(max = 8, message = "{validation.oa.no-rule-save.separator-size}")
        private String separator;

        /** 是否启用 */
        private Integer enabled;

        /** 备注 */
        @Size(max = 255, message = "{validation.oa.no-rule-save.remark-size}")
        private String remark;
    }

    /**
     * 附件命令。
     */
    @Data
    public static class AttachmentSave {

        /** whale-file 文件ID */
        @NotNull(message = "{validation.oa.attachment-save.file-id-required}")
        private Long fileId;

        /** 文件名 */
        @Size(max = 255, message = "{validation.oa.attachment-save.file-name-size}")
        private String fileName;

        /** 文件大小 */
        private Long fileSize;

        /** MIME 类型 */
        private String contentType;
    }

    /**
     * 发文保存命令。
     */
    @Data
    public static class OutgoingSave {

        /** 标题 */
        @NotBlank(message = "{validation.oa.outgoing-save.title-required}")
        @Size(max = 255, message = "{validation.oa.outgoing-save.title-size}")
        private String title;

        /** 文种 */
        private String docType;

        /** 密级 */
        @Min(value = 1, message = "{validation.oa.outgoing-save.security-level-min}")
        @Max(value = 3, message = "{validation.oa.outgoing-save.security-level-max}")
        private Integer securityLevel;

        /** 紧急度 */
        @Min(value = 1, message = "{validation.oa.outgoing-save.urgency-min}")
        @Max(value = 3, message = "{validation.oa.outgoing-save.urgency-max}")
        private Integer urgency;

        /** 模板ID */
        private Long templateId;

        /** 拟稿部门ID */
        private Long deptId;

        /** 主送 */
        @Size(max = 512, message = "{validation.oa.outgoing-save.main-recipients-size}")
        private String mainRecipients;

        /** 抄送 */
        @Size(max = 512, message = "{validation.oa.outgoing-save.copy-recipients-size}")
        private String copyRecipients;

        /** 正文 */
        private String content;

        /** 备注 */
        @Size(max = 255, message = "{validation.oa.outgoing-save.remark-size}")
        private String remark;

        /** 附件 */
        @Valid
        private List<AttachmentSave> attachments;
    }

    /**
     * 发文审核命令。
     */
    @Data
    public static class OutgoingAudit {

        /** 是否通过 */
        @NotNull(message = "{validation.oa.outgoing-audit.approved-required}")
        private Boolean approved;
    }

    /**
     * 发文签发命令。
     */
    @Data
    public static class OutgoingSign {

        /** 签发人 */
        @Size(max = 64, message = "{validation.oa.outgoing-sign.signer-size}")
        private String signer;

        /** 签发日期 */
        private LocalDate signDate;
    }

    /**
     * 收文登记命令。
     */
    @Data
    public static class IncomingSave {

        /** 来文单位 */
        @NotBlank(message = "{validation.oa.incoming-save.source-unit-required}")
        @Size(max = 128, message = "{validation.oa.incoming-save.source-unit-size}")
        private String sourceUnit;

        /** 来文文号 */
        @Size(max = 128, message = "{validation.oa.incoming-save.source-no-size}")
        private String sourceNo;

        /** 标题 */
        @NotBlank(message = "{validation.oa.incoming-save.title-required}")
        @Size(max = 255, message = "{validation.oa.incoming-save.title-size}")
        private String title;

        /** 密级 */
        @Min(value = 1, message = "{validation.oa.incoming-save.security-level-min}")
        @Max(value = 3, message = "{validation.oa.incoming-save.security-level-max}")
        private Integer securityLevel;

        /** 紧急度 */
        @Min(value = 1, message = "{validation.oa.incoming-save.urgency-min}")
        @Max(value = 3, message = "{validation.oa.incoming-save.urgency-max}")
        private Integer urgency;

        /** 收文日期 */
        private LocalDate receivedDate;

        /** 办理期限 */
        private LocalDate dueDate;

        /** 备注 */
        @Size(max = 255, message = "{validation.oa.incoming-save.remark-size}")
        private String remark;

        /** 附件 */
        @Valid
        private List<AttachmentSave> attachments;
    }

    /**
     * 收文批阅命令。
     */
    @Data
    public static class IncomingReview {

        /** 批阅意见 */
        @NotBlank(message = "{validation.oa.incoming-review.instruction-required}")
        @Size(max = 1024, message = "{validation.oa.incoming-review.instruction-size}")
        private String instruction;

        /** 承办人用户ID */
        private Long assigneeId;

        /** 承办人姓名 */
        @Size(max = 64, message = "{validation.oa.incoming-review.assignee-name-size}")
        private String assigneeName;
    }

    /**
     * 收文转发命令。
     */
    @Data
    public static class IncomingForward {

        /** 承办人用户ID */
        private Long assigneeId;

        /** 承办人姓名 */
        @Size(max = 64, message = "{validation.oa.incoming-forward.assignee-name-size}")
        private String assigneeName;

        /** 备注 */
        @Size(max = 255, message = "{validation.oa.incoming-forward.remark-size}")
        private String remark;
    }
}
