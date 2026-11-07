package com.bluewhale.oa.document.application.view;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 公文模块视图集合。
 *
 * @author BlueWhale
 */
@Data
public class DocumentViews {

    /**
     * 模板视图。
     */
    @Data
    public static class Template {

        private Long id;

        private String code;

        private String name;

        private String docType;

        private Integer version;

        private String status;

        private String headerText;

        private String footerText;

        private String bodyTemplate;

        private String remark;

        private LocalDateTime createTime;

        private LocalDateTime updateTime;
    }

    /**
     * 文号规则视图。
     */
    @Data
    public static class NoRule {

        private Long id;

        private String code;

        private String name;

        private String docType;

        private String prefix;

        private String periodFormat;

        private Integer serialLength;

        private String separator;

        private Integer enabled;

        private String remark;

        private LocalDateTime createTime;

        private LocalDateTime updateTime;
    }

    /**
     * 附件视图。
     */
    @Data
    public static class Attachment {

        private Long id;

        private Long fileId;

        private String fileName;

        private Long fileSize;

        private String contentType;

        private Integer sortNo;
    }

    /**
     * 归档映射视图。
     */
    @Data
    public static class ArchiveRef {

        private Long id;

        private String docType;

        private Long docId;

        private String docNo;

        private String docTitle;

        private String sourceType;

        private String sourceId;

        private String archiveItemId;

        private String status;

        private String errorMessage;

        private LocalDateTime pushedAt;

        private LocalDateTime createTime;
    }

    /**
     * 发文视图。
     */
    @Data
    public static class Outgoing {

        private Long id;

        private String docNo;

        private String title;

        private String docType;

        private Integer securityLevel;

        private Integer urgency;

        private Long templateId;

        private String templateName;

        private Long drafterId;

        private String drafterName;

        private Long deptId;

        private String mainRecipients;

        private String copyRecipients;

        private String content;

        private String signer;

        private LocalDate signDate;

        private LocalDateTime issuedAt;

        private String status;

        private Long approvalInstanceId;

        private String archiveItemId;

        private String remark;

        private LocalDateTime createTime;

        private LocalDateTime updateTime;

        /** 附件 */
        private List<Attachment> attachments;

        /** 归档映射 */
        private ArchiveRef archiveRef;
    }

    /**
     * 收文视图。
     */
    @Data
    public static class Incoming {

        private Long id;

        private String incomingNo;

        private String sourceUnit;

        private String sourceNo;

        private String title;

        private Integer securityLevel;

        private Integer urgency;

        private LocalDate receivedDate;

        private LocalDate dueDate;

        private Long assigneeId;

        private String assigneeName;

        private String instruction;

        private LocalDateTime handledAt;

        private String status;

        private String archiveItemId;

        private String remark;

        private LocalDateTime createTime;

        private LocalDateTime updateTime;

        /** 附件 */
        private List<Attachment> attachments;

        /** 归档映射 */
        private ArchiveRef archiveRef;
    }
}
