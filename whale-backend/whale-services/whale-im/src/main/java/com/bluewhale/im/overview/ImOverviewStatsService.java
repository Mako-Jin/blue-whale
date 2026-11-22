package com.bluewhale.im.overview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.im.entity.ImAgentBinding;
import com.bluewhale.im.entity.ImAgentRun;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImMessage;
import com.bluewhale.im.mapper.ImAgentBindingMapper;
import com.bluewhale.im.mapper.ImAgentRunMapper;
import com.bluewhale.im.mapper.ImConversationMapper;
import com.bluewhale.im.mapper.ImMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 传信司（IM）统计口径：会话三态、今日/近七日消息量、静默会话、机器人的三笔账。
 *
 * <p><b>为什么主账是"消息量"而不是"会话数"。</b>IM 的会话是长命的：一个群建起来
 * 就一直躺在列表里，哪怕三个月没人说话。只看会话数，屏风上会永远显示一个体面的
 * 数字，而实际上这座驿站已经没人传信了。所以这一屏把会话数当"存量背景"，
 * 把<b>近七日每天发出多少条消息</b>当成主图——它掉到 0 的时候，会话数不会有任何变化。</p>
 *
 * <p><b>机器人的三笔账单独算。</b>{@code WAITING_CONFIRM} 是"写操作在等人点头"，
 * 没人点它就永远停在那里；{@code FAILED} 是用户只看到一句"助手开小差"。这两笔在
 * IM 的会话里不会自己冒出来，只有在这里数得出来。绑定数只报启用的，停用的不参与。</p>
 *
 * <p><b>不写 GROUP BY，按条件各 count 一次。</b>状态码值固定、条件都能走索引，
 * 几次索引 count 比一次聚合直观；更重要的是不走自定义 SQL 就不用自己拼
 * {@code tenant_id}——租户隔离由 MyBatis-Plus 插件保证，一旦漏了就是跨租户串数
 * 且不报错（内城营造成批的老教训）。近七日是把同一条 count 按天跑七次，
 * 换来的是"哪一天掉的"一眼可见，而不是一个加起来的总数。</p>
 *
 * <p><b>租户 ID 必须显式传入。</b>概览 provider 在独立线程里执行，SDK 会把租户
 * 绑到执行线程（{@code OverviewService} 负责），这里是第二道闸：传 {@code null}
 * 直接抛——缺租户说明调用方配错了，宁可让屏风降级，也不要在上面显示一个跨租户的和。</p>
 *
 * <p>统计只读，不写库：概览被前端按 30s 轮询，任何写操作都会变成"每 30 秒写一次"。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ImOverviewStatsService {

    /** 消息状态：正常（0 是已撤回，撤回的消息不该算"传出去了"） */
    static final int MESSAGE_STATUS_NORMAL = 1;

    /** 静默会话窗口（天）：七日没有任何动静 */
    static final int IDLE_WINDOW_DAYS = 7;

    /** 消息趋势窗口（天）：屏风上一屏放得下的柱数 */
    static final int TREND_WINDOW_DAYS = 7;

    /** 机器人绑定：启用 */
    static final int BINDING_ENABLED = 1;

    /** 智能体运行状态：等待人工确认（写操作卡在这里不会自己往下走） */
    static final String RUN_WAITING_CONFIRM = "WAITING_CONFIRM";

    /** 智能体运行状态：失败 */
    static final String RUN_FAILED = "FAILED";

    /** 会话类型：单聊 */
    static final String TYPE_SINGLE = "SINGLE";

    /** 会话类型：群聊 */
    static final String TYPE_GROUP = "GROUP";

    /** 会话类型：机器人会话 */
    static final String TYPE_BOT = "BOT";

    private final ImConversationMapper conversationMapper;

    private final ImMessageMapper messageMapper;

    private final ImAgentBindingMapper bindingMapper;

    private final ImAgentRunMapper runMapper;

    /**
     * 取一次统计快照。
     *
     * @param tenantId 租户 ID（必填）
     * @return 统计快照
     */
    public ImOverviewStats snapshot(Long tenantId) {
        return snapshot(tenantId, LocalDateTime.now());
    }

    /**
     * 取一次统计快照（时间可注入，便于断言窗口边界）。
     *
     * @param tenantId 租户 ID（必填）
     * @param now 当前时刻
     * @return 统计快照
     */
    ImOverviewStats snapshot(Long tenantId, LocalDateTime now) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for im overview stats");
        }
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime idleSince = now.minusDays(IDLE_WINDOW_DAYS);
        LocalDateTime trendSince = now.minusDays(TREND_WINDOW_DAYS);

        long total = count(conversationMapper.selectCount(new LambdaQueryWrapper<ImConversation>()));
        long single = countByType(TYPE_SINGLE);
        long group = countByType(TYPE_GROUP);
        long bot = countByType(TYPE_BOT);
        long activeToday = count(conversationMapper.selectCount(new LambdaQueryWrapper<ImConversation>()
                .isNotNull(ImConversation::getLastActiveAt)
                .ge(ImConversation::getLastActiveAt, todayStart)));
        /* 静默只数"动过又停了"的会话：{@code last_active_at} 为空的会话是建了还没
           说过话的空壳，混进来会把"刚建的群"和"散了的群"显示成同一个数。 */
        long idle7d = count(conversationMapper.selectCount(new LambdaQueryWrapper<ImConversation>()
                .isNotNull(ImConversation::getLastActiveAt)
                .lt(ImConversation::getLastActiveAt, idleSince)));

        long messageToday = countMessages(todayStart, todayStart.plusDays(1));
        long message7d = countMessages(trendSince, null);
        List<ImOverviewStats.DailyCount> recentDays = dailyCounts(now.toLocalDate());

        long botEnabled = count(bindingMapper.selectCount(new LambdaQueryWrapper<ImAgentBinding>()
                .eq(ImAgentBinding::getEnabled, BINDING_ENABLED)));
        long pendingConfirm = count(runMapper.selectCount(new LambdaQueryWrapper<ImAgentRun>()
                .eq(ImAgentRun::getStatus, RUN_WAITING_CONFIRM)));
        long runFailed7d = count(runMapper.selectCount(new LambdaQueryWrapper<ImAgentRun>()
                .eq(ImAgentRun::getStatus, RUN_FAILED)
                .ge(ImAgentRun::getCreateTime, trendSince)));

        return new ImOverviewStats(total, single, group, bot, activeToday, idle7d,
                messageToday, message7d, botEnabled, pendingConfirm, runFailed7d, recentDays);
    }

    /**
     * 按会话类型计数。
     *
     * @param type 会话类型
     * @return 该类型的会话数
     */
    private long countByType(String type) {
        return count(conversationMapper.selectCount(new LambdaQueryWrapper<ImConversation>()
                .eq(ImConversation::getType, type)));
    }

    /**
     * 数一段窗口内的正常消息（不含已撤回）。
     *
     * @param from 起始时刻（含）
     * @param to 结束时刻（不含）；{@code null} 表示不设上界
     * @return 消息数
     */
    private long countMessages(LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<ImMessage>()
                .eq(ImMessage::getStatus, MESSAGE_STATUS_NORMAL)
                .ge(ImMessage::getCreateTime, from);
        if (to != null) {
            wrapper.lt(ImMessage::getCreateTime, to);
        }
        return count(messageMapper.selectCount(wrapper));
    }

    /**
     * 近七日逐日消息量（升序，今天在最后）。
     *
     * <p>按天各查一次而不是一条 GROUP BY：条件里只有日期常量，租户条件仍由插件
     * 追加；一旦自拼 SQL 漏了 {@code tenant_id}，这条曲线会把别家的消息也算进来，
     * 而且看起来完全正常。</p>
     *
     * @param today 今天的日期
     * @return 逐日计数
     */
    private List<ImOverviewStats.DailyCount> dailyCounts(LocalDate today) {
        List<ImOverviewStats.DailyCount> days = new ArrayList<>(TREND_WINDOW_DAYS);
        for (int back = TREND_WINDOW_DAYS - 1; back >= 0; back--) {
            LocalDate day = today.minusDays(back);
            LocalDateTime start = day.atStartOfDay();
            days.add(new ImOverviewStats.DailyCount(day, countMessages(start, start.plusDays(1))));
        }
        return days;
    }

    /**
     * count 结果兜底：MyBatis-Plus 在无行时可能给 {@code null}。
     *
     * @param value 计数
     * @return 非空计数
     */
    private static long count(Long value) {
        return value == null ? 0L : value;
    }
}
