package com.bluewhale.forum.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 论坛服务配置属性（{@code whale.forum.*}）。
 *
 * <p>全部上限可经环境变量覆盖，默认值即规格 v1.3 §九「非功能上限」的落地值；
 * 改默认值必须先改规格，否则验收判据与实现会悄悄分叉。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.forum")
public class ForumProperties {

    /** 标题长度上限（字符），超出返回 30002 */
    private int maxTitleLength = 120;

    /** 正文长度上限（富文本源码字符数），超出返回 30003 */
    private int maxContentLength = 100_000;

    /** 单帖楼层上限，达到后帖子转 closed（30011） */
    private int maxPostsPerThread = 5_000;

    /** 分页默认条数 */
    private int pageSizeDefault = 20;

    /** 分页条数上限（超出取值 = 上限） */
    private int pageSizeMax = 100;

    /** 列表页码上限，超过后强制游标分页 */
    private int maxPageNumber = 500;

    /** 单帖 @ 人数上限 */
    private int maxMentionsPerPost = 20;

    /** 同一用户两次发帖最小间隔（秒） */
    private int postIntervalSeconds = 60;

    /** 发帖滑动窗口长度（秒）：窗口内发帖数上限见 {@link #postWindowMax} */
    private int postWindowSeconds = 600;

    /** 滑动窗口内发帖数上限（规格 F-03-17：10 分钟 5 帖） */
    private int postWindowMax = 5;

    /** 同一用户两次回帖最小间隔（秒） */
    private int replyIntervalSeconds = 10;

    /** 作者可编辑自身帖子的时间窗（分钟），超时需治理权限 */
    private int editWindowMinutes = 30;

    /** 摘要截断长度（字符） */
    private int summaryLength = 200;

    /** 引用块摘要长度（字符）：引用只是一行灰字提示，太长会把楼层列表挤爆 */
    private int quoteExcerptLength = 80;

    /** 热榜快照刷新周期（分钟） */
    private int hotRefreshMinutes = 30;

    /** 审核模式版块内，发布态编辑是否自动退回待审（规格 §6.6 硬规则，默认开启） */
    private boolean auditOnEdit = true;

    /** 匿名编号 HMAC 密钥；为空时匿名发帖开关强制失效（fail-closed，避免编号可预测） */
    private String anonymousSecret = "";

    /** 新用户前 N 帖强制送审（0 关闭；F-09-03「新用户前 3 帖必审」） */
    private int newUserAuditThreads = 3;

    /** 举报去重窗口（小时）：同人对同目标在窗口内只受理一条 */
    private int reportDuplicateHours = 24;

    /** 通知最多重试次数（超过转 FAILED，等人工排查） */
    private int notifyMaxRetry = 5;

    /** whale-message 内网基础地址（站内信经 /message/internal/send 投递） */
    private String messageBaseUrl = "http://127.0.0.1:8094";

    /** whale-service-auth 内网基础地址（@提及经 /user/v1/internal/directory 解析昵称） */
    private String authBaseUrl = "http://127.0.0.1:8081";

    /**
     * 通讯录解析连接超时（毫秒）。
     *
     * <p>@提及是发帖的<b>附属能力</b>：通讯录慢或不可达时必须在毫秒级放弃，
     * 不能让「@了谁」把「帖子发出去」拖住——默认 RestClient 无超时，会一直等。</p>
     */
    private int directoryConnectTimeoutMillis = 800;

    /** 通讯录解析读超时（毫秒） */
    private int directoryReadTimeoutMillis = 1_500;

    /** 浏览去重窗口（小时）：同人同帖窗口内只计一次浏览 */
    private int viewDedupeHours = 24;

    /** 互动（投花/藏帖）固定窗口长度（秒） */
    private int reactionWindowSeconds = 60;

    /** 互动窗口内次数上限（规格 v1.3：投花 60/分；0 关闭） */
    private int reactionWindowMax = 60;

    /** 热榜滚动窗口（天） */
    private int hotWindowDays = 7;

    /** 热榜取前 N 名 */
    private int hotTopN = 20;

    /** 热榜窗口小时数：24h 档窗口（24 小时内发布的帖子参榜） */
    private int hotWindowHours24 = 24;

    /**
     * 24h 档小时衰减常数 τ（小时）。
     *
     * <p>衰减系数 = {@code EXP(-发帖小时数 / τ)}，τ 越大衰减越慢。7d 档不衰减
     * （长周期榜看累计热度，衰减会把老帖整体清出，7 天窗口本身就是衰减）。</p>
     */
    private int hotDecayHalfLifeHours = 24;

    /** 热度分加成：精华帖（题金榜）额外加的分 */
    private double hotEssenceBonus = 50;

    /** 热度分权重：回复 */
    private double hotReplyWeight = 3;

    /** 热度分权重：投花 */
    private double hotLikeWeight = 5;

    /** 热度分权重：浏览（浏览最容易刷，权重最低） */
    private double hotViewWeight = 0.2;

    /** 热度分扣分权重：每条违规处置轨迹扣的分 */
    private double hotPenaltyWeight = 5;

    /** 是否启用热榜定时刷新（多实例各自刷新，结果幂等覆盖） */
    private boolean hotRefreshEnabled = true;

    /**
     * 检索用纯文本的保留长度（字符）。
     *
     * <p>正文上限 10 万字符，但没人靠第 8 万字命中；截断到 4000 字符可把
     * {@code content_text} 控制在几十 KB 内，全文索引体积与重建耗时都降一个量级。</p>
     */
    private int searchTextLength = 4_000;

    /**
     * 走全文索引的最小关键词长度；0 表示按数据库 {@code ngram_token_size} 自动取值。
     *
     * <p>ngram 默认分词长度 2，比它短的关键词（单字）在索引里根本不存在，
     * 必须回退 {@code LIKE}，否则单字搜索恒空。</p>
     */
    private int searchMinKeywordLength = 0;

    /** 检索重建单批条数（批量提交，避免一次 UPDATE 十万行锁表） */
    private int searchReindexBatch = 200;

    /** 是否启用计数对账定时任务 */
    private boolean countReconcileEnabled = true;

    /** 计数对账单批扫描帖子数 */
    private int countReconcileBatch = 500;

    /** 计数对账 cron（默认每日 03:30，避开备份与热榜刷新） */
    private String countReconcileCron = "0 30 3 * * ?";

    /** 论坛经验：发帖（THREAD_PUBLISH，匿名帖不计） */
    private int expThreadPublish = 5;

    /** 论坛经验：被投花（LIKE_GIVEN，每帖只计一次，匿名帖不计） */
    private int expLikeGiven = 2;

    /** 论坛经验：题金榜（ESSENCE_AWARD，匿名帖不计） */
    private int expEssenceAward = 200;

    /** 论坛经验：7d 金榜第一名（HOT_TOP1，匿名帖不计） */
    private int expHotTop1 = 1000;

    /** 经验流水单次查询条数上限 */
    private int expLedgerPageSize = 20;
}
