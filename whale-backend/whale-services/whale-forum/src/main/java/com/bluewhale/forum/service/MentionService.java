package com.bluewhale.forum.service;

import java.util.List;

/**
 * @提及服务（F-03-10）。
 *
 * <p>一次提及处理包含四步：<b>解析 → 解析身份 → 落库 → 通知</b>。
 * 全链路都按「附属能力」对待：任何一步失败只跳过该提及，不抛异常、不回滚帖子——
 * 为了 @ 一个人把帖子发不出去，是比漏通知严重得多的故障。</p>
 *
 * @author BlueWhale
 */
public interface MentionService {

    /** 来源：发帖（主帖正文） */
    String SOURCE_THREAD = "THREAD";

    /** 来源：回帖 */
    String SOURCE_POST = "POST";

    /** 主帖在提及表里的楼层号（1 楼由 fm_thread 代表，不落 fm_post） */
    long MAIN_POST_ID = 0L;

    /**
     * 解析并派发提及。
     *
     * @param threadId 帖子ID
     * @param postId 楼层ID（主帖传 {@link #MAIN_POST_ID}）
     * @param source 来源：{@link #SOURCE_THREAD} / {@link #SOURCE_POST}
     * @param richTextContent 已消毒的富文本正文（内部转纯文本后解析）
     * @param threadTitle 帖子标题（用于通知文案）
     * @param authorId 发起人用户ID（自己 @ 自己不通知）
     * @return 实际发出通知的人数（解析不到/重复/自己 @ 自己均不计）；
     *         在事务内调用时返回 {@code 0}，实际派发推迟到事务提交后执行
     */
    int dispatch(Long threadId, Long postId, String source, String richTextContent,
                 String threadTitle, Long authorId);

    /**
     * 把一段文本里的 @昵称 解析成用户ID列表（供发帖前预校验/前端回显）。
     *
     * @param richTextContent 富文本正文
     * @return 解析到的用户ID（去重、保持出现顺序）
     */
    List<Long> resolveUserIds(String richTextContent);}
