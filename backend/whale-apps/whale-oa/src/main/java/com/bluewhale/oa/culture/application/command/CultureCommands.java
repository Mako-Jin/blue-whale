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

    private CultureCommands() {
    }

    /**
     * 文化动态保存命令。
     */
    @Data
    public static class EventSave {

        /** 标题 */
        @NotBlank(message = "标题不能为空")
        @Size(max = 128, message = "标题不能超过 128 个字符")
        private String title;

        /** 类型：MILESTONE / ACTIVITY / NEWS */
        private String eventType;

        /** 正文 */
        private String content;

        /** 发生日期 */
        private LocalDate eventDate;

        /** 备注 */
        @Size(max = 255, message = "备注不能超过 255 个字符")
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
        @NotNull(message = "祝福对象不能为空")
        private Long targetUserId;

        /** 祝福对象姓名 */
        private String targetName;

        /** 祝福内容 */
        @NotBlank(message = "祝福内容不能为空")
        @Size(max = 512, message = "祝福内容不能超过 512 个字符")
        private String content;
    }

    /**
     * 投票保存命令。
     */
    @Data
    public static class PollSave {

        /** 投票标题 */
        @NotBlank(message = "投票标题不能为空")
        @Size(max = 128, message = "投票标题不能超过 128 个字符")
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
        @NotBlank(message = "选项内容不能为空")
        @Size(max = 255, message = "选项内容不能超过 255 个字符")
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
        @NotEmpty(message = "请至少选择一个选项")
        private List<Long> optionIds;
    }

    /**
     * 建议提交命令。
     */
    @Data
    public static class SuggestionSave {

        /** 建议标题 */
        @NotBlank(message = "建议标题不能为空")
        @Size(max = 128, message = "建议标题不能超过 128 个字符")
        private String title;

        /** 类别：SUGGESTION / COMPLAINT / PRAISE / OTHER */
        private String category;

        /** 建议内容 */
        @NotBlank(message = "建议内容不能为空")
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
        @NotBlank(message = "回复内容不能为空")
        private String content;

        /** 目标状态（可选，默认 REPLIED） */
        private String status;
    }
}
