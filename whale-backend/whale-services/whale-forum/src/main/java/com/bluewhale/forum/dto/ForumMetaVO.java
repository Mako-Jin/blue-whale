package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 论坛门面视图：枚举与上限一次性下发。
 *
 * <p>前端硬编码一份枚举的后果是「后端加了第四种帖子类型，前端下拉里没有」，
 * 且这种缺口不报错，只在用户眼里表现为「新功能没生效」。因此枚举与上限
 * 一律由服务端下发，前端只做展示映射。</p>
 *
 * @author BlueWhale
 */
@Data
public class ForumMetaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务名（五同铁律） */
    private String service;

    /** 版本标识 */
    private String version;

    /** 帖子类型：NORMAL/QA/VOTE/NOTICE/ACTIVITY/ARTICLE */
    private List<String> threadTypes;

    /** 帖子状态：DRAFT/SCHEDULED/PENDING/PUBLISHED/SOLVED/CLOSED/REJECTED/DELETED */
    private List<String> threadStatuses;

    /** 版块可见性：ALL/ROLE/DEPT */
    private List<String> boardVisibilities;

    /** 审核模式：POST（先发后审）/ PRE（先审后发） */
    private List<String> auditModes;

    /** 举报状态：PENDING/HANDLING/RESOLVED/REJECTED */
    private List<String> reportStatuses;

    /** 互动类型：LIKE（投花）/ COLLECT（藏帖） */
    private List<String> reactionTypes;

    /** 处置动作：AUDIT_PASS/AUDIT_REJECT/PIN/ESSENCE/LOCK/REMOVE/MUTE/BAN 等 */
    private List<String> moderationActions;

    /** 匿名发帖是否可用（HMAC 密钥未配置时禁用） */
    private boolean anonymousEnabled;

    /** 非功能上限（与规格 v1.3 §九 一致） */
    private Limits limits;

    /**
     * 非功能上限视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class Limits implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 标题长度上限 */
        private int maxTitleLength;

        /** 正文长度上限 */
        private int maxContentLength;

        /** 单帖楼层上限 */
        private int maxPostsPerThread;

        /** 分页默认条数 */
        private int pageSizeDefault;

        /** 分页条数上限 */
        private int pageSizeMax;

        /** 列表页码上限（超过后强制游标分页） */
        private int maxPageNumber;

        /** 单帖 @ 人数上限 */
        private int maxMentionsPerPost;

        /** 发帖最小间隔（秒） */
        private int postIntervalSeconds;

        /** 回帖最小间隔（秒） */
        private int replyIntervalSeconds;

        /** 热榜快照刷新周期（分钟） */
        private int hotRefreshMinutes;
    }
}
