package com.bluewhale.oa.culture.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业文化命令集合。
 *
 * @author BlueWhale
 */
public final class CultureCommands {

    /** 工具类，禁止实例化。 */
    private CultureCommands() {
    }

    /**
     * 文化动态保存命令。
     */
    @Data
    public static class EventSave {

        /** 标题 */
        @NotBlank(message = "{validation.oa.event-save.title-required}")
        @Size(max = 128, message = "{validation.oa.event-save.title-size}")
        private String title;

        /** 类型：MILESTONE / ACTIVITY / NEWS */
        private String eventType;

        /** 正文 */
        private String content;

        /** 发生日期 */
        private LocalDate eventDate;

        /** 备注 */
        @Size(max = 255, message = "{validation.oa.event-save.remark-size}")
        private String remark;
    }

    /**
     * 祝福发送命令。
     */
    @Data
    public static class WishSave {

        /** 祝福类型：BIRTHDAY / ANNIVERSARY */
        private String wishType;

        /** 祝福对象用户ID */
        @NotNull(message = "{validation.oa.wish-save.target-user-id-required}")
        private Long targetUserId;

        /** 祝福对象姓名 */
        private String targetName;

        /** 祝福内容 */
        @NotBlank(message = "{validation.oa.wish-save.content-required}")
        @Size(max = 512, message = "{validation.oa.wish-save.content-size}")
        private String content;
    }

    /**
     * 投票保存命令。
     */
    @Data
    public static class PollSave {

        /** 投票标题 */
        @NotBlank(message = "{validation.oa.poll-save.title-required}")
        @Size(max = 128, message = "{validation.oa.poll-save.title-size}")
        private String title;

        /** 投票说明 */
        private String description;

        /** 是否多选 */
        private Boolean multipleChoice;

        /** 是否匿名 */
        private Boolean anonymous;

        /** 最多可选数量 */
        private Integer maxChoices;

        /** 开始时间 */
        private LocalDateTime startTime;

        /** 结束时间 */
        private LocalDateTime endTime;

        /** 选项 */
        private List<PollOptionSave> options;
    }

    /**
     * 投票选项命令。
     */
    @Data
    public static class PollOptionSave {

        /** 选项文本 */
        @NotBlank(message = "{validation.oa.poll-option-save.option-text-required}")
        @Size(max = 255, message = "{validation.oa.poll-option-save.option-text-size}")
        private String optionText;

        /** 排序号 */
        private Integer sortNo;
    }

    /**
     * 投票命令。
     */
    @Data
    public static class VoteCommand {

        /** 选中的选项ID */
        @NotEmpty(message = "{validation.oa.vote.option-ids-required}")
        private List<Long> optionIds;
    }

    /**
     * 建议提交命令。
     */
    @Data
    public static class SuggestionSave {

        /** 建议标题 */
        @NotBlank(message = "{validation.oa.suggestion-save.title-required}")
        @Size(max = 128, message = "{validation.oa.suggestion-save.title-size}")
        private String title;

        /** 类别：SUGGESTION / COMPLAINT / PRAISE / OTHER */
        private String category;

        /** 建议内容 */
        @NotBlank(message = "{validation.oa.suggestion-save.content-required}")
        private String content;

        /** 是否匿名 */
        private Boolean anonymous;

        /** 提交人部门ID */
        private Long deptId;
    }

    /**
     * 建议回复命令。
     */
    @Data
    public static class SuggestionReplySave {

        /** 回复内容 */
        @NotBlank(message = "{validation.oa.suggestion-reply-save.content-required}")
        private String content;

        /** 目标状态（可选，默认 REPLIED） */
        private String status;
    }
}
