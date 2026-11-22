package com.bluewhale.forum.overview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.forum.common.enums.ReportStatus;
import com.bluewhale.forum.common.enums.ThreadStatus;
import com.bluewhale.forum.entity.ForumAppeal;
import com.bluewhale.forum.entity.ForumReport;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.entity.ForumUserBan;
import com.bluewhale.forum.mapper.ForumAppealMapper;
import com.bluewhale.forum.mapper.ForumReportMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.mapper.ForumUserBanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 清议堂（FORUM）统计口径：帖子五态、举报待办、申诉待理、封禁存量。
 *
 * <p><b>口径只在这里定义一次。</b>帖子状态取
 * {@link ThreadStatus}（待审 / 在议 / 已解决 / 已关闭 / 已驳回）；举报待办算
 * {@link ReportStatus#PENDING} 与 {@link ReportStatus#HANDLING} 两段——处理中的
 * 工单还没出结论，仍是欠账；{@code REJECTED}（举报不成立）与 {@code RESOLVED}
 * 都已闭环，不挂待办。</p>
 *
 * <p><b>不写 GROUP BY，按状态各 count 一次。</b>状态码值固定且都有索引，
 * 几次索引 count 比一次聚合直观；更重要的是不走自定义 SQL 就不用自己拼
 * {@code tenant_id} 条件——租户隔离由 MyBatis-Plus 插件保证，一旦漏了就是
 * 跨租户串数且不报错（营造成批的老教训）。</p>
 *
 * <p><b>租户 ID 必须显式传入。</b>概览 provider 在独立线程里执行，SDK 会把
 * 租户绑到执行线程（{@code OverviewService} 负责），这里是第二道闸：
 * 传 {@code null} 直接抛——缺租户说明调用方配错了，宁可让屏风降级，
 * 也不要在上面显示一个跨租户的和。</p>
 *
 * <p>统计只读，不写库：概览被前端按 30s 轮询，任何写操作都会变成"每 30 秒写一次"。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ForumOverviewStatsService {

    /** 帖子状态：待审 */
    private static final String THREAD_PENDING = ThreadStatus.PENDING.name();

    /** 帖子状态：已发布（在议） */
    private static final String THREAD_PUBLISHED = ThreadStatus.PUBLISHED.name();

    /** 帖子状态：已解决 */
    private static final String THREAD_SOLVED = ThreadStatus.SOLVED.name();

    /** 帖子状态：已关闭 */
    private static final String THREAD_CLOSED = ThreadStatus.CLOSED.name();

    /** 帖子状态：已驳回 */
    private static final String THREAD_REJECTED = ThreadStatus.REJECTED.name();

    /** 举报状态：待受理 */
    private static final String REPORT_PENDING = ReportStatus.PENDING.name();

    /** 举报状态：处理中 */
    private static final String REPORT_HANDLING = ReportStatus.HANDLING.name();

    /** 待办举报的两段：待受理 + 处理中（都没出结论） */
    private static final List<String> REPORT_OPEN = List.of(REPORT_PENDING, REPORT_HANDLING);

    /** 申诉状态：待理（与 {@code AppealServiceImpl} 的 STATUS_PENDING 一致） */
    private static final String APPEAL_PENDING = "PENDING";

    /** 封禁状态：生效中 */
    private static final String BAN_ACTIVE = "ACTIVE";

    /** 悬榜（置顶）标记值 */
    private static final Integer YES = 1;

    private final ForumThreadMapper threadMapper;

    private final ForumReportMapper reportMapper;

    private final ForumAppealMapper appealMapper;

    private final ForumUserBanMapper userBanMapper;

    /**
     * 取一次统计快照。
     *
     * @param tenantId 租户 ID（必填）
     * @return 统计快照
     */
    public ForumOverviewStats snapshot(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for forum overview stats");
        }
        long pending = countThread(THREAD_PENDING);
        long published = countThread(THREAD_PUBLISHED);
        long solved = countThread(THREAD_SOLVED);
        long closed = countThread(THREAD_CLOSED);
        long rejected = countThread(THREAD_REJECTED);
        long pinned = count(threadMapper.selectCount(new LambdaQueryWrapper<ForumThread>()
                .eq(ForumThread::getPinned, YES)
                .eq(ForumThread::getStatus, THREAD_PUBLISHED)));
        long essence = count(threadMapper.selectCount(new LambdaQueryWrapper<ForumThread>()
                .eq(ForumThread::getEssence, YES)
                .eq(ForumThread::getStatus, THREAD_PUBLISHED)));
        long reportPending = count(reportMapper.selectCount(new LambdaQueryWrapper<ForumReport>()
                .in(ForumReport::getStatus, REPORT_OPEN)));
        long appealPending = count(appealMapper.selectCount(new LambdaQueryWrapper<ForumAppeal>()
                .eq(ForumAppeal::getStatus, APPEAL_PENDING)));
        long banActive = count(userBanMapper.selectCount(new LambdaQueryWrapper<ForumUserBan>()
                .eq(ForumUserBan::getStatus, BAN_ACTIVE)));
        return new ForumOverviewStats(pending, published, solved, closed, rejected,
                pinned, essence, reportPending, appealPending, banActive);
    }

    /**
     * 按状态数帖子。
     *
     * @param status 帖子状态码
     * @return 帖数
     */
    private long countThread(String status) {
        return count(threadMapper.selectCount(new LambdaQueryWrapper<ForumThread>()
                .eq(ForumThread::getStatus, status)));
    }

    /**
     * 计数兜底：{@code selectCount} 在极端情况下返回 {@code null}，直接拆箱就是 NPE。
     *
     * @param value 计数值，可空
     * @return 非空计数
     */
    private static long count(Long value) {
        return value == null ? 0L : value;
    }
}
