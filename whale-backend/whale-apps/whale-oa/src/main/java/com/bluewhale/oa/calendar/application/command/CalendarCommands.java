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
        @NotBlank(message = "{validation.oa.entry.title-required}")
        @Size(max = 128, message = "{validation.oa.entry.title-size}")
        private String title;

        /** 日程描述 */
        private String description;

        /** 地点 */
        @Size(max = 128, message = "{validation.oa.entry.location-size}")
        private String location;

        /** 开始时间 */
        @NotNull(message = "{validation.oa.entry.start-time-required}")
        private LocalDateTime startTime;

        /** 结束时间 */
        @NotNull(message = "{validation.oa.entry.end-time-required}")
        private LocalDateTime endTime;

        /** 是否全天：0 否 / 1 是 */
        private Integer allDay;

        /** 可见性：PRIVATE / SHARED / PUBLIC */
        private String visibility;

        /** 提前提醒分钟数（0 表示不提醒） */
        @Min(value = 0, message = "{validation.oa.entry.remind-minutes-min}")
        @Max(value = 10080, message = "{validation.oa.entry.remind-minutes-max}")
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
        @NotNull(message = "{validation.oa.share-user.user-id-required}")
        private Long userId;

        /** 用户姓名 */
        private String userName;
    }
}
