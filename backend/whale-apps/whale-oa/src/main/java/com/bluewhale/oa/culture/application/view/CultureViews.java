package com.bluewhale.oa.culture.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业文化视图集合。
 *
 * @author BlueWhale
 */
public final class CultureViews {

    private CultureViews() {
    }

    /**
     * 文化动态视图。
     */
    @Data
    public static class Event {

        /** 主键ID */
        private Long id;

        /** 类型 */
        private String eventType;

        /** 标题 */
        private String title;

        /** 正文 */
        private String content;

        /** 发生日期 */
        private LocalDate eventDate;

        /** 封面文件ID */
        private Long coverFileId;

        /** 状态 */
        private String status;

        /** 发布人用户ID */
        private Long publisherId;

        /** 发布人姓名 */
        private String publisherName;

        /** 发布时间 */
        private LocalDateTime publishTime;

        /** 备注 */
        private String remark;

        /** 创建时间 */
        private LocalDateTime createTime;
    }

    /**
     * 祝福视图。
     */
    @Data
    public static class Wish {

        /** 主键ID */
        private Long id;

        /** 祝福类型 */
        private String wishType;

        /** 祝福对象用户ID */
        private Long targetUserId;

        /** 祝福对象姓名 */
        private String targetName;

        /** 祝福内容 */
        private String content;

        /** 发送人用户ID */
        private Long senderId;

        /** 发送人姓名 */
        private String senderName;

        /** 祝福日期 */
        private LocalDate wishDate;

        /** 创建时间 */
        private LocalDateTime createTime;
    }

    /**
     * 生日 / 入职周年视图。
     */
    @Data
    public static class Birthday {

        /** 用户ID */
        private Long userId;

        /** 用户姓名 */
        private String userName;

        /** 部门ID */
        private Long deptId;

        /** 生日 */
        private LocalDate birthday;

        /** 入职日期 */
        private LocalDate hireDate;

        /** 周年年限（生日可为空） */
        private Integer years;

        /** 类型：BIRTHDAY / ANNIVERSARY */
        private String type;
    }

    /**
     * 投票视图。
     */
    @Data
    public static class Poll {

        /** 主键ID */
        private Long id;

        /** 标题 */
        private String title;

        /** 说明 */
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

        /** 状态 */
        private String status;

        /** 创建人用户ID */
        private Long creatorId;

        /** 创建人姓名 */
        private String creatorName;

        /** 创建时间 */
        private LocalDateTime createTime;
    }

    /**
     * 投票选项视图。
     */
    @Data
    public static class PollOption {

        /** 主键ID */
        private Long id;

        /** 投票ID */
        private Long pollId;

        /** 选项文本 */
        private String optionText;

        /** 排序号 */
        private Integer sortNo;

        /** 得票数 */
        private Long voteCount;

        /** 当前用户是否选择 */
        private Boolean myChoice;
    }

    /**
     * 投票详情视图。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PollDetail extends Poll {

        /** 选项 */
        private List<PollOption> options;

        /** 当前用户是否已投 */
        private Boolean myVoted;

        /** 总票数 */
        private Long totalVotes;
    }

    /**
     * 建议视图（匿名时隐藏提交人）。
     */
    @Data
    public static class Suggestion {

        /** 主键ID */
        private Long id;

        /** 标题 */
        private String title;

        /** 类别 */
        private String category;

        /** 内容 */
        private String content;

        /** 是否匿名 */
        private Boolean anonymous;

        /** 提交人用户ID（匿名时为 null） */
        private Long submitterId;

        /** 提交人姓名（匿名时为 null） */
        private String submitterName;

        /** 提交人部门ID */
        private Long deptId;

        /** 状态 */
        private String status;

        /** 处理人用户ID */
        private Long handlerId;

        /** 处理人姓名 */
        private String handlerName;

        /** 最近处理时间 */
        private LocalDateTime handledTime;

        /** 创建时间 */
        private LocalDateTime createTime;
    }

    /**
     * 建议回复视图。
     */
    @Data
    public static class SuggestionReply {

        /** 主键ID */
        private Long id;

        /** 建议ID */
        private Long suggestionId;

        /** 回复人用户ID */
        private Long replierId;

        /** 回复人姓名 */
        private String replierName;

        /** 回复内容 */
        private String content;

        /** 创建时间 */
        private LocalDateTime createTime;
    }

    /**
     * 建议详情视图。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SuggestionDetail extends Suggestion {

        /** 回复列表 */
        private List<SuggestionReply> replies;
    }
}
