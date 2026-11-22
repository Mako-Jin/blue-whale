package com.bluewhale.im.overview;

import java.time.LocalDate;
import java.util.List;

/**
 * 传信司（IM）一次统计快照。
 *
 * <p>会话三态（单聊 / 群聊 / 机器人会话）互斥且合计等于 {@link #conversationTotal()}，
 * 与柱图不用同一份数据：柱图看的是<b>近七日每天发出多少条消息</b>——会话数是存量，
 * 消息量才是"驿路还通不通"。</p>
 *
 * <p>{@link #activeToday()} 与 {@link #idle7d()} 都按会话的 {@code lastActiveAt} 判：
 * 一个会话"今天动过"和"七天没动"是同一根轴的两头，中间那一段不必再切——切得越细，
 * 屏风上越没人看得懂。</p>
 *
 * @param conversationTotal 在册会话数（未删除）
 * @param conversationSingle 单聊会话数
 * @param conversationGroup 群聊会话数
 * @param conversationBot 机器人会话数
 * @param activeToday 今日有过消息的会话数
 * @param idle7d 近 7 日没有任何消息的会话数（含今天之前就没动过的）
 * @param messageToday 今日正常消息数（不含已撤回）
 * @param message7d 近 7 日正常消息数
 * @param botEnabled 启用中的机器人绑定数
 * @param pendingConfirm 等待人工确认的智能体运行数（WAITING_CONFIRM）
 * @param runFailed7d 近 7 日失败的智能体运行数
 * @param recentDays 近 7 日消息量，按日期升序（最早的一天在前，今天在最后）
 * @author BlueWhale
 */
public record ImOverviewStats(
        long conversationTotal,
        long conversationSingle,
        long conversationGroup,
        long conversationBot,
        long activeToday,
        long idle7d,
        long messageToday,
        long message7d,
        long botEnabled,
        long pendingConfirm,
        long runFailed7d,
        List<DailyCount> recentDays) {

    /**
     * 近七日中的一天。
     *
     * @param day 日期
     * @param count 当日正常消息数
     */
    public record DailyCount(LocalDate day, long count) {
    }
}
