package com.bluewhale.oa.collab.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 协同业务视图集合。
 *
 * @author BlueWhale
 */
public final class CollabViews {

    /** 工具类，禁止实例化。 */
    private CollabViews() {
    }

    /**
     * 会议室视图。
     */
    @Data
    public static class RoomView {

        private Long id;

        private String name;

        private String location;

        private Integer capacity;

        private String status;

        private String remark;

        private LocalDateTime createTime;
    }

    /**
     * 参会人视图。
     */
    @Data
    public static class ParticipantView {

        private Long id;

        private Long userId;

        private String userName;

        private Integer required;

        private String status;

        private String remark;
    }

    /**
     * 会议纪要视图。
     */
    @Data
    public static class MinuteView {

        private Long id;

        private Long meetingId;

        private String content;

        private String author;

        private Long authorId;

        private LocalDateTime updateTime;
    }

    /**
     * 会议行动项视图。
     */
    @Data
    public static class ActionView {

        private Long id;

        private String content;

        private Long ownerId;

        private String ownerName;

        private LocalDate dueDate;

        private String status;

        private Long taskId;
    }

    /**
     * 会议列表视图。
     */
    @Data
    public static class MeetingView {

        private Long id;

        private String code;

        private String title;

        private Long roomId;

        private String roomName;

        private String organizer;

        private Long organizerId;

        private Long deptId;

        private LocalDateTime startTime;

        private LocalDateTime endTime;

        private String agenda;

        private String status;

        private String remark;

        private Integer participantCount;

        private LocalDateTime createTime;
    }

    /**
     * 会议详情视图。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MeetingDetailView extends MeetingView {

        private List<ParticipantView> participants;

        private MinuteView minute;

        private List<ActionView> actions;
    }

    /**
     * 公告接收人视图。
     */
    @Data
    public static class RecipientView {

        private Long id;

        private Long userId;

        private String userName;

        private Integer readFlag;

        private LocalDateTime readTime;
    }

    /**
     * 公告视图。
     */
    @Data
    public static class NoticeView {

        private Long id;

        private String title;

        private String content;

        private String scopeType;

        private String scopeValue;

        private String status;

        private String publisher;

        private Long publisherId;

        private LocalDateTime publishTime;

        private String remark;

        private LocalDateTime createTime;

        private Integer myReadFlag;

        private LocalDateTime myReadTime;

        private Long recipientCount;

        private Long readCount;
    }

    /**
     * 公告详情视图。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class NoticeDetailView extends NoticeView {

        private List<RecipientView> recipients;
    }

    /**
     * 通讯录候选视图。
     */
    @Data
    public static class CandidateView {

        private Long userId;

        private String username;

        private String nickname;

        private Long deptId;
    }

    /**
     * 任务视图。
     */
    @Data
    public static class TaskView {

        private Long id;

        private String title;

        private String description;

        private Long ownerId;

        private String ownerName;

        private Long creatorId;

        private String creatorName;

        private String priority;

        private String status;

        private LocalDate dueDate;

        private String source;

        private Long sourceId;

        private LocalDateTime finishTime;

        private LocalDateTime createTime;
    }

    /**
     * 任务评论视图。
     */
    @Data
    public static class CommentView {

        private Long id;

        private Long taskId;

        private Long userId;

        private String userName;

        private String content;

        private LocalDateTime createTime;
    }
}
