package com.bluewhale.oa.calendar.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程业务视图集合。
 *
 * @author BlueWhale
 */
public final class CalendarViews {

    /** 工具类，禁止实例化。 */
    private CalendarViews() {
    }

    /**
     * 日程列表视图。
     */
    @Data
    public static class EntryView {

        private Long id;

        private Long ownerId;

        private String ownerName;

        private String title;

        private String description;

        private String location;

        private LocalDateTime startTime;

        private LocalDateTime endTime;

        private Integer allDay;

        private String visibility;

        private Integer remindMinutes;

        private String source;

        private Long sourceId;

        private LocalDateTime createTime;
    }

    /**
     * 共享用户视图。
     */
    @Data
    public static class ShareView {

        private Long userId;

        private String userName;
    }

    /**
     * 日程详情视图。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EntryDetailView extends EntryView {

        /** 是否可编辑（仅所有者） */
        private boolean editable;

        /** 共享名单（仅所有者可见） */
        private List<ShareView> shares;
    }
}
