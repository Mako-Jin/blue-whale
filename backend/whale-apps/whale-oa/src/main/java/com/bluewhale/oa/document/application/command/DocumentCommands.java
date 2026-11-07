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
        @NotBlank(message = "模板编码不能为空")
        @Size(max = 64, message = "模板编码不能超过 64 字")
        private String code;

        /** 模板名称 */
        @NotBlank(message = "模板名称不能为空")
        @Size(max = 128, message = "模板名称不能超过 128 字")
        private String name;

        /** 适用范围 */
        @NotBlank(message = "适用范围不能为空")
        private String docType;

        /** 版本号 */
        @Min(value = 1, message = "版本号最小为 1")
        private Integer version;

        /** 状态 */
        private String status;

        /** 红头文字 */
        @Size(max = 512, message = "红头文字不能超过 512 字")
        private String headerText;

        /** 版记文字 */
        @Size(max = 512, message = "版记文字不能超过 512 字")
        private String footerText;

        /** 正文模板 */
        private String bodyTemplate;

        /** 备注 */
        @Size(max = 255, message = "备注不能超过 255 字")
        private String remark;
    }

    /**
     * 文号规则保存命令。
     */
    @Data
    public static class NoRuleSave {

        /** 规则编码 */
        @NotBlank(message = "规则编码不能为空")
        @Size(max = 64, message = "规则编码不能超过 64 字")
        private String code;

        /** 规则名称 */
        @NotBlank(message = "规则名称不能为空")
        @Size(max = 128, message = "规则名称不能超过 128 字")
        private String name;

        /** 适用范围 */
        @NotBlank(message = "适用范围不能为空")
        private String docType;

        /** 文号前缀 */
        @Size(max = 64, message = "文号前缀不能超过 64 字")
        private String prefix;

        /** 期间格式 */
        private String periodFormat;

        /** 流水位数 */
        @Min(value = 3, message = "流水位数最小为 3")
        @Max(value = 6, message = "流水位数最大为 6")
        private Integer serialLength;

        /** 分隔符 */
        @Size(max = 8, message = "分隔符不能超过 8 字")
        private String separator;

        /** 是否启用 */
        private Integer enabled;

        /** 备注 */
        @Size(max = 255, message = "备注不能超过 255 字")
        private String remark;
    }

    /**
     * 附件命令。
     */
    @Data
    public static class AttachmentSave {

        /** whale-file 文件ID */
        @NotNull(message = "附件文件ID不能为空")
        private Long fileId;

        /** 文件名 */
        @Size(max = 255, message = "附件文件名不能超过 255 字")
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
        @NotBlank(message = "公文标题不能为空")
        @Size(max = 255, message = "公文标题不能超过 255 字")
        private String title;

        /** 文种 */
        private String docType;

        /** 密级 */
        @Min(value = 1, message = "密级取值 1-3")
        @Max(value = 3, message = "密级取值 1-3")
        private Integer securityLevel;

        /** 紧急度 */
        @Min(value = 1, message = "紧急度取值 1-3")
        @Max(value = 3, message = "紧急度取值 1-3")
        private Integer urgency;

        /** 模板ID */
        private Long templateId;

        /** 拟稿部门ID */
        private Long deptId;

        /** 主送 */
        @Size(max = 512, message = "主送不能超过 512 字")
        private String mainRecipients;

        /** 抄送 */
        @Size(max = 512, message = "抄送不能超过 512 字")
        private String copyRecipients;

        /** 正文 */
        private String content;

        /** 备注 */
        @Size(max = 255, message = "备注不能超过 255 字")
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
        @NotNull(message = "审核结论不能为空")
        private Boolean approved;
    }

    /**
     * 发文签发命令。
     */
    @Data
    public static class OutgoingSign {

        /** 签发人 */
        @Size(max = 64, message = "签发人不能超过 64 字")
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
        @NotBlank(message = "来文单位不能为空")
        @Size(max = 128, message = "来文单位不能超过 128 字")
        private String sourceUnit;

        /** 来文文号 */
        @Size(max = 128, message = "来文文号不能超过 128 字")
        private String sourceNo;

        /** 标题 */
        @NotBlank(message = "公文标题不能为空")
        @Size(max = 255, message = "公文标题不能超过 255 字")
        private String title;

        /** 密级 */
        @Min(value = 1, message = "密级取值 1-3")
        @Max(value = 3, message = "密级取值 1-3")
        private Integer securityLevel;

        /** 紧急度 */
        @Min(value = 1, message = "紧急度取值 1-3")
        @Max(value = 3, message = "紧急度取值 1-3")
        private Integer urgency;

        /** 收文日期 */
        private LocalDate receivedDate;

        /** 办理期限 */
        private LocalDate dueDate;

        /** 备注 */
        @Size(max = 255, message = "备注不能超过 255 字")
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
        @NotBlank(message = "批阅意见不能为空")
        @Size(max = 1024, message = "批阅意见不能超过 1024 字")
        private String instruction;

        /** 承办人用户ID */
        private Long assigneeId;

        /** 承办人姓名 */
        @Size(max = 64, message = "承办人姓名不能超过 64 字")
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
        @Size(max = 64, message = "承办人姓名不能超过 64 字")
        private String assigneeName;

        /** 备注 */
        @Size(max = 255, message = "备注不能超过 255 字")
        private String remark;
    }
}
