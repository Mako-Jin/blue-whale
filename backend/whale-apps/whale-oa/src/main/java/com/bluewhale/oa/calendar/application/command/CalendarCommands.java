package com.bluewhale.oa.calendar.application.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程业务命令集合。
 *
 * @author BlueWhale
 */
public final class CalendarCommands {

    /** 工具类，禁止实例化。 */
    private CalendarCommands() {
    }

    /**
     * 日程保存命令。
     */
    @Data
    public static class EntrySaveCommand {

        /** 日程标题 */
        @NotBlank(message = "日程标题不能为空")
        @Size(max = 128, message = "日程标题不能超过 128 个字符")
        private String title;

        /** 日程描述 */
        private String description;

        /** 地点 */
        @Size(max = 128, message = "地点不能超过 128 个字符")
        private String location;

        /** 开始时间 */
        @NotNull(message = "开始时间不能为空")
        private LocalDateTime startTime;

        /** 结束时间 */
        @NotNull(message = "结束时间不能为空")
        private LocalDateTime endTime;

        /** 是否全天：0 否 / 1 是 */
        private Integer allDay;

        /** 可见性：PRIVATE / SHARED / PUBLIC */
        private String visibility;

        /** 提前提醒分钟数（0 表示不提醒） */
        @Min(value = 0, message = "提醒分钟数不能为负数")
        @Max(value = 10080, message = "提醒分钟数不能超过 7 天")
        private Integer remindMinutes;

        /** 所有者用户ID（控制器注入） */
        private Long ownerId;

        /** 所有者姓名（控制器注入） */
        private String ownerName;
    }

    /**
     * 共享名单保存命令（整体替换）。
     */
    @Data
    public static class ShareSaveCommand {

        /** 被共享用户 */
        private List<ShareUserCommand> users;
    }

    /**
     * 共享用户。
     */
    @Data
    public static class ShareUserCommand {

        /** 用户ID */
        @NotNull(message = "共享用户ID不能为空")
        private Long userId;

        /** 用户姓名 */
        private String userName;
    }
}
