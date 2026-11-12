package com.bluewhale.auth.application.port;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 口令到期提醒查询端口（REQ-SEC-023）。
 *
 * <p>应用层只声明「谁需要提醒」与「提醒已发送」两个语义；具体跨租户落库查询由
 * {@code infrastructure} 适配实现，避免应用层依赖 MyBatis 与会话租户上下文。</p>
 *
 * @author BlueWhale
 */
public interface PasswordExpiryQuery {

    /**
     * 查询需要到期提醒的启用账号
     *
     * <p>筛选口径：已启用、最近改密时间不晚于 {@code remindFrom}，且当前口令周期内尚未提醒过
     * （提醒时间早于最近改密时间）。{@code remindFrom} 取所有租户策略中最早的提醒窗口，
     * 调用方再按候选账号所属租户的策略做二次筛选，避免为每个租户单独发起一次全表扫描。</p>
     *
     * @param remindFrom 提醒窗口起点（最近改密时间不晚于该时刻即已进入某租户的提醒窗口）
     * @return 待提醒账号列表；无候选时返回空列表
     */
    List<PasswordExpiryCandidate> findCandidates(LocalDateTime remindFrom);

    /**
     * 标记指定用户已提醒（写入最近提醒时间）
     *
     * @param userId 用户 ID
     * @param noticedAt 提醒时刻
     */
    void markNoticed(Long userId, LocalDateTime noticedAt);

    /**
     * 待提醒账号快照
     *
     * @param tenantId 所属租户 ID
     * @param userId 用户 ID
     * @param username 用户名
     * @param passwordUpdatedTime 最近改密时间
     * @author BlueWhale
     */
    record PasswordExpiryCandidate(Long tenantId, Long userId, String username,
                                   LocalDateTime passwordUpdatedTime) {
    }
}
