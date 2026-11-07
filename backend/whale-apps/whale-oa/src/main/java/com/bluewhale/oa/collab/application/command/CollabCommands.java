package com.bluewhale.oa.collab.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 协同业务命令集合。
 *
 * @author BlueWhale
 */
public final class CollabCommands {

    private CollabCommands() {
    }

    /**
     * 会议室保存命令。
     */
    @Data
    public static class RoomSaveCommand {

        /** 会议室名称 */
        @NotBlank(message = "会议室名称不能为空")
        @Size(max = 64, message = "会议室名称不能超过 64 个字符")
        private String name;

        /** 位置 */
        @Size(max = 128, message = "位置不能超过 128 个字符")
        private String location;

        /** 容纳人数 */
        private Integer capacity;

        /** 状态 */
        private String status;

        /** 备注 */
        @Size(max = 255, message = "备注不能超过 255 个字符")
        private String remark;
    }

    /**
     * 会议保存命令。
     */
    @Data
    public static class MeetingSaveCommand {

        /** 会议主题 */
        @NotBlank(message = "会议主题不能为空")
        @Size(max = 128, message = "会议主题不能超过 128 个字符")
        private String title;

        /** 会议室ID */
        private Long roomId;

        /** 组织人账号 */
        private String organizer;

        /** 组织人用户ID */
        private Long organizerId;

        /** 组织部门ID */
        private Long deptId;

        /** 开始时间 */
        @NotNull(message = "会议开始时间不能为空")
        private LocalDateTime startTime;

        /** 结束时间 */
        @NotNull(message = "会议结束时间不能为空")
        private LocalDateTime endTime;

        /** 会议议程 */
        private String agenda;

        /** 备注 */
        @Size(max = 255, message = "备注不能超过 255 个字符")
        private String remark;

        /** 参会人 */
        private List<ParticipantCommand> participants;
    }

    /**
     * 参会人命令。
     */
    @Data
    public static class ParticipantCommand {

        /** 参会人用户ID */
        @NotNull(message = "参会人用户ID不能为空")
        private Long userId;

        /** 参会人姓名 */
        private String userName;

        /** 是否必到：0 否 / 1 是 */
        private Integer required;

        /** 备注 */
        private String remark;
    }

    /**
     * 会议状态变更命令。
     */
    @Data
    public static class MeetingStatusCommand {

        /** 目标状态 */
        @NotBlank(message = "目标状态不能为空")
        private String status;
    }

    /**
     * 参会状态变更命令。
     */
    @Data
    public static class ParticipantStatusCommand {

        /** 目标状态 */
        @NotBlank(message = "参会状态不能为空")
        private String status;

        /** 备注 */
        private String remark;
    }

    /**
     * 会议纪要保存命令。
     */
    @Data
    public static class MinuteSaveCommand {

        /** 纪要内容 */
        @NotBlank(message = "纪要内容不能为空")
        private String content;

        /** 纪要作者账号 */
        private String author;

        /** 纪要作者用户ID */
        private Long authorId;

        /** 行动项（传入后整体替换） */
        private List<ActionCommand> actions;
    }

    /**
     * 会议行动项命令。
     */
    @Data
    public static class ActionCommand {

        /** 行动项内容 */
        @NotBlank(message = "行动项内容不能为空")
        @Size(max = 255, message = "行动项内容不能超过 255 个字符")
        private String content;

        /** 负责人用户ID */
        @NotNull(message = "行动项负责人不能为空")
        private Long ownerId;

        /** 负责人姓名 */
        private String ownerName;

        /** 截止日期 */
        private LocalDate dueDate;

        /** 是否自动生成个人任务 */
        private Boolean createTask;
    }

    /**
     * 公告保存命令。
     */
    @Data
    public static class NoticeSaveCommand {

        /** 公告标题 */
        @NotBlank(message = "公告标题不能为空")
        @Size(max = 128, message = "公告标题不能超过 128 个字符")
        private String title;

        /** 公告正文 */
        @NotBlank(message = "公告正文不能为空")
        private String content;

        /** 发布范围 */
        private String scopeType;

        /** 范围值 */
        private String scopeValue;

        /** 备注 */
        @Size(max = 255, message = "备注不能超过 255 个字符")
        private String remark;

        /** 接收人（为空时按范围从通讯录解析） */
        private List<RecipientCommand> recipients;
    }

    /**
     * 公告接收人命令。
     */
    @Data
    public static class RecipientCommand {

        /** 接收人用户ID */
        @NotNull(message = "接收人用户ID不能为空")
        private Long userId;

        /** 接收人姓名 */
        private String userName;
    }

    /**
     * 任务保存命令。
     */
    @Data
    public static class TaskSaveCommand {

        /** 任务标题 */
        @NotBlank(message = "任务标题不能为空")
        @Size(max = 128, message = "任务标题不能超过 128 个字符")
        private String title;

        /** 任务描述 */
        private String description;

        /** 负责人用户ID */
        @NotNull(message = "任务负责人不能为空")
        private Long ownerId;

        /** 负责人姓名 */
        private String ownerName;

        /** 优先级 */
        private String priority;

        /** 截止日期 */
        private LocalDate dueDate;

        /** 来源 */
        private String source;

        /** 来源业务ID */
        private Long sourceId;

        /** 创建人用户ID（服务端可由当前登录用户覆盖） */
        private Long creatorId;

        /** 创建人姓名 */
        private String creatorName;
    }

    /**
     * 任务状态变更命令。
     */
    @Data
    public static class TaskStatusCommand {

        /** 目标状态 */
        @NotBlank(message = "任务状态不能为空")
        private String status;
    }

    /**
     * 任务评论命令。
     */
    @Data
    public static class CommentSaveCommand {

        /** 评论内容 */
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 500, message = "评论内容不能超过 500 个字符")
        private String content;

        /** 评论人用户ID */
        private Long userId;

        /** 评论人姓名 */
        private String userName;
    }
}
