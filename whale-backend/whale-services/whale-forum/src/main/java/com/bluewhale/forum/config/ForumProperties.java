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

    /** 热榜快照刷新周期（分钟） */
    private int hotRefreshMinutes = 30;

    /** 审核模式版块内，发布态编辑是否自动退回待审（规格 §6.6 硬规则，默认开启） */
    private boolean auditOnEdit = true;

    /** 匿名编号 HMAC 密钥；为空时匿名发帖开关强制失效（fail-closed，避免编号可预测） */
    private String anonymousSecret = "";
}
