package com.bluewhale.mail.overview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.entity.MailDeliveryReport;
import com.bluewhale.mail.mapper.MailAccountMapper;
import com.bluewhale.mail.mapper.MailDeliveryReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 邮驿（MAIL）统计口径：账号四态、配额吃紧、近七日退信。
 *
 * <p><b>为什么盯同步而不是盯邮件数。</b>平台里的信不是"推"进来的，是同步流程
 * 按 IMAP 一趟趟"拉"进来的：同步一停，屏风上的信就停在昨天，而屏风本身不会
 * 报错——它只是一直显示旧信。所以账号的同步状态才是这一屏的主账：
 * 近一日已同步、超时未同步、从未同步、未开同步，四格互斥且合计等于在册数。</p>
 *
 * <p><b>配额与退信是另外两笔。</b>配额按 {@code used_bytes * 100 >= quota_bytes * 80}
 * 判，条件是常量、不带用户输入（{@code apply} 走参数占位符，租户条件仍由插件
 * 追加在 WHERE 上）；{@code quota_bytes = 0} 表示"配额未知"，不参与判定——
 * 把未知算成吃紧会让值班的人天天看到假警报。退信只数近 7 日，且区分永久（5.x.x）
 * 与临时（4.x.x）：永久退信说明地址废了，临时退信对端多半会自动重投。</p>
 *
 * <p><b>不写 GROUP BY，按状态各 count 一次。</b>状态码值固定且都有索引，几次
 * 索引 count 比一次聚合直观；更重要的是不走自定义 SQL 就不用自己拼
 * {@code tenant_id} 条件——租户隔离由 MyBatis-Plus 插件保证，一旦漏了就是
 * 跨租户串数且不报错（营造成批的老教训）。</p>
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
public class MailOverviewStatsService {

    /** 账号状态：启用（与 {@code MailAccountServiceImpl.STATUS_ENABLED} 一致） */
    static final int STATUS_ENABLED = 1;

    /** 同步开关：开 */
    static final int SYNC_ON = 1;

    /** 同步新鲜度窗口（小时）：超过这个时长没拉信就算超时 */
    static final int SYNC_WINDOW_HOURS = 24;

    /** 配额告警线（百分比）：用到八成该提醒扩容或清理 */
    static final int QUOTA_WARN_PERCENT = 80;

    /** 退信统计窗口（天） */
    static final int BOUNCE_WINDOW_DAYS = 7;

    /** 退信类型：永久（RFC 3463 的 5.x.x） */
    static final String BOUNCE_HARD = "HARD";

    private final MailAccountMapper accountMapper;

    private final MailDeliveryReportMapper reportMapper;

    /**
     * 取一次统计快照。
     *
     * @param tenantId 租户 ID（必填）
     * @return 统计快照
     */
    public MailOverviewStats snapshot(Long tenantId) {
        return snapshot(tenantId, LocalDateTime.now());
    }

    /**
     * 取一次统计快照（时间可注入，便于断言窗口边界）。
     *
     * @param tenantId 租户 ID（必填）
     * @param now      当前时刻
     * @return 统计快照
     */
    MailOverviewStats snapshot(Long tenantId, LocalDateTime now) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for mail overview stats");
        }
        long total = count(accountMapper.selectCount(new LambdaQueryWrapper<MailAccount>()));
        long enabled = count(accountMapper.selectCount(new LambdaQueryWrapper<MailAccount>()
                .eq(MailAccount::getStatus, STATUS_ENABLED)));
        long syncEnabled = count(accountMapper.selectCount(new LambdaQueryWrapper<MailAccount>()
                .eq(MailAccount::getSyncEnabled, SYNC_ON)));
        long never = count(accountMapper.selectCount(new LambdaQueryWrapper<MailAccount>()
                .eq(MailAccount::getSyncEnabled, SYNC_ON)
                .isNull(MailAccount::getLastSyncTime)));
        long overdue = count(accountMapper.selectCount(new LambdaQueryWrapper<MailAccount>()
                .eq(MailAccount::getSyncEnabled, SYNC_ON)
                .isNotNull(MailAccount::getLastSyncTime)
                .lt(MailAccount::getLastSyncTime, syncSince(now))));
        /* 三态相减得"新鲜"，比再查一次 "last_sync_time >= x" 少一条 SQL；
           万一脏数据让差值为负（同一行同时命中两个条件），夹到 0 而不是让
           柱图出现负数——负数的柱子在屏幕上会画到基线以下。 */
        long fresh = Math.max(0L, syncEnabled - never - overdue);
        long quotaTight = count(accountMapper.selectCount(new LambdaQueryWrapper<MailAccount>()
                .gt(MailAccount::getQuotaBytes, 0L)
                .apply("used_bytes * 100 >= quota_bytes * {0}", QUOTA_WARN_PERCENT)));
        LocalDateTime bounceSince = bounceSince(now);
        long bounce7d = count(reportMapper.selectCount(new LambdaQueryWrapper<MailDeliveryReport>()
                .ge(MailDeliveryReport::getCreateTime, bounceSince)));
        long bounceHard7d = count(reportMapper.selectCount(new LambdaQueryWrapper<MailDeliveryReport>()
                .ge(MailDeliveryReport::getCreateTime, bounceSince)
                .eq(MailDeliveryReport::getBounceType, BOUNCE_HARD)));
        return new MailOverviewStats(total, enabled, Math.max(0L, total - enabled),
                fresh, overdue, never, Math.max(0L, total - syncEnabled),
                quotaTight, bounce7d, bounceHard7d);
    }

    /**
     * 同步新鲜度的下界：早于这个时刻算超时。
     *
     * @param now 当前时刻
     * @return 下界时刻
     */
    static LocalDateTime syncSince(LocalDateTime now) {
        return now.minusHours(SYNC_WINDOW_HOURS);
    }

    /**
     * 退信统计窗口的起点。
     *
     * @param now 当前时刻
     * @return 起点时刻
     */
    static LocalDateTime bounceSince(LocalDateTime now) {
        return now.minusDays(BOUNCE_WINDOW_DAYS);
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
