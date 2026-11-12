package com.bluewhale.auth.infrastructure.password;

import com.bluewhale.auth.application.model.TenantPasswordPolicy;
import com.bluewhale.auth.application.service.TenantPasswordPolicyService;
import com.bluewhale.auth.application.port.PasswordExpiryQuery;
import com.bluewhale.auth.application.port.PasswordExpiryQuery.PasswordExpiryCandidate;
import com.bluewhale.common.jobschedule.ScheduledJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 口令到期提醒任务（REQ-SEC-023）：每日扫描即将到期/已到期的启用账号并输出告警日志。
 *
 * <p><b>多租户口径</b>：有效期与提醒窗口按账号所属租户的生效策略计算（租户管理员可配置，
 * 未配置回退全局默认）。为减少扫描次数，先取所有租户中最早的提醒窗口做一次跨租户查询，
 * 再按候选账号的租户策略二次筛选；未进入该租户提醒窗口的账号不告警、不写提醒时间。</p>
 *
 * <p>同一口令周期内仅提醒一次（由 {@code password_notice_time} 与最近改密时间比较保证）。
 * 任务异常只记录日志，不中断调度线程；日志中的到期告警可由日志采集侧配置为通知通道
 * （邮件/IM）的触发源。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordExpiryNoticeJob {

    /** 口令到期提醒查询端口 */
    private final PasswordExpiryQuery passwordExpiryQuery;

    /** 租户口令策略服务（按租户计算有效期与提醒窗口） */
    private final TenantPasswordPolicyService tenantPasswordPolicyService;

    /**
     * 扫描并在口令到期前 {@code warnDays} 天输出告警；已到期账号按「已过期」分级告警
     */
    @ScheduledJob(code = "auth.password.expiry-notice",
            cron = "${whale.auth.password.notice-cron:0 0 2 * * ?}",
            timeoutSeconds = 1800)
    public String notifyExpiringPasswords() {
        Map<Long, TenantPasswordPolicy> policies = tenantPasswordPolicyService.effectivePolicies();
        int minWindowDays = minReminderWindowDays(policies);
        if (minWindowDays < 0) {
            return "skipped: no tenant has password expiry enabled";
        }
        LocalDateTime now = LocalDateTime.now();
        List<PasswordExpiryCandidate> candidates =
                passwordExpiryQuery.findCandidates(now.minusDays(minWindowDays));
        if (candidates.isEmpty()) {
            return "candidates=0, notified=0";
        }
        int notified = 0;
        for (PasswordExpiryCandidate candidate : candidates) {
            if (notifyIfDue(candidate, now, policies.get(candidate.tenantId()))) {
                notified++;
            }
        }
        log.info("口令到期提醒完成: 候选账号数={}, 本次提醒账号数={}", candidates.size(), notified);
        return "candidates=" + candidates.size() + ", notified=" + notified;
    }

    /**
     * 计算全部租户中最短的提醒窗口（天）；无启用有效期的租户时返回 -1
     *
     * @param policies 租户生效策略
     * @return 最短提醒窗口天数；全部租户均未启用到期时返回 -1
     */
    private static int minReminderWindowDays(Map<Long, TenantPasswordPolicy> policies) {
        return policies.values().stream()
                .filter(policy -> policy.maxAgeDays() > 0)
                .mapToInt(PasswordExpiryNoticeJob::reminderWindowDays)
                .min()
                .orElse(-1);
    }

    /**
     * 计算单个租户的提醒窗口：有效期减去提前提醒天数，最小为 0
     *
     * @param policy 租户策略
     * @return 提醒窗口天数
     */
    private static int reminderWindowDays(TenantPasswordPolicy policy) {
        return Math.max(policy.maxAgeDays() - policy.warnDays(), 0);
    }

    /**
     * 按租户策略判定候选账号是否需要提醒；需要时输出告警并记录提醒时间
     *
     * @param candidate 候选账号
     * @param now 当前时刻
     * @param policy 账号所属租户的生效策略
     * @return 本次已提醒返回 true；未进入该租户提醒窗口或策略不适用时返回 false
     */
    private boolean notifyIfDue(PasswordExpiryCandidate candidate, LocalDateTime now,
                                TenantPasswordPolicy policy) {
        if (policy == null || policy.maxAgeDays() <= 0 || candidate.passwordUpdatedTime() == null) {
            return false;
        }
        int windowDays = reminderWindowDays(policy);
        if (candidate.passwordUpdatedTime().isAfter(now.minusDays(windowDays))) {
            return false;
        }
        long remainingDays = Duration.between(now,
                candidate.passwordUpdatedTime().plusDays(policy.maxAgeDays())).toDays();
        if (remainingDays < 0) {
            log.warn("账号口令已过期: tenantId={}, userId={}, username={}, overdueDays={}",
                    candidate.tenantId(), candidate.userId(), candidate.username(), -remainingDays);
        } else {
            log.warn("账号口令即将到期: tenantId={}, userId={}, username={}, remainingDays={}",
                    candidate.tenantId(), candidate.userId(), candidate.username(), remainingDays);
        }
        passwordExpiryQuery.markNoticed(candidate.userId(), now);
        return true;
    }
}
