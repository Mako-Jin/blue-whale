package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.model.entity.DeviceRecord;
import com.bluewhale.auth.domain.model.entity.LoginHistoryRecord;
import com.bluewhale.auth.domain.model.entity.RefreshTokenRecord;
import com.bluewhale.auth.domain.model.entity.RememberMeTokenRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 会话设备仓储端口（权限中心扩展 M3）：聚合登录历史 / 设备 / refreshToken 记录 /
 * remember-me 记录四类 append-only 存储的读写契约，由 infrastructure 适配实现。
 *
 * <p><b>ADR #7</b>：账本写入失败不得阻断登录，实现侧或调用方须降级为告警。</p>
 *
 * @author BlueWhale
 */
public interface SessionLedgerRepository {

    /**
     * 追加登录历史（append-only，禁止更新）
     *
     * @param record 登录历史记录
     */
    void appendLoginHistory(LoginHistoryRecord record);

    /**
     * 分页查询登录历史（按 loginTime 降序）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID（可空表示不限）
     * @param result 结果过滤（SUCCESS / FAILURE / LOGOUT，可空表示不限）
     * @param startTime 起始时间（可空）
     * @param endTime 截止时间（可空）
     * @param current 当前页码
     * @param size 每页条数
     * @return 登录历史分页结果
     */
    LoginHistoryPage pageLoginHistory(Long tenantId, Long userId, String result,
                                      LocalDateTime startTime, LocalDateTime endTime,
                                      long current, long size);

    /**
     * 按（租户, 用户, 指纹）查询设备，不存在时以快照登记新设备
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param deviceFingerprint 设备指纹
     * @param deviceName 设备名称（可空）
     * @param deviceType 设备类型（可空）
     * @param os 操作系统快照（可空）
     * @param browser 浏览器快照（可空）
     * @return 设备实体（存在则已刷新快照与最近使用时间）
     */
    DeviceRecord upsertDevice(Long tenantId, Long userId, String deviceFingerprint, String deviceName,
                              String deviceType, String os, String browser);

    /**
     * 按主键查询设备
     *
     * @param id 设备 ID
     * @return 设备实体；不存在时返回空
     */
    Optional<DeviceRecord> findDeviceById(Long id);

    /**
     * 查询用户设备列表（按最近使用时间降序）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 设备列表
     */
    List<DeviceRecord> listDevicesByUser(Long tenantId, Long userId);

    /**
     * 保存设备实体（信任标记 / 快照变更落库）
     *
     * @param device 设备实体
     * @return 回填主键后的实体
     */
    DeviceRecord saveDevice(DeviceRecord device);

    /**
     * 登记 refreshToken 会话记录（登录 / 刷新换发时写入）
     *
     * @param record 会话记录
     */
    void appendRefreshToken(RefreshTokenRecord record);

    /**
     * 按 jti 查询会话记录
     *
     * @param jti 令牌唯一标识
     * @return 会话记录；不存在时返回空
     */
    Optional<RefreshTokenRecord> findRefreshTokenByJti(String jti);

    /**
     * 保存会话记录（撤销标记落库）
     *
     * @param record 会话记录
     */
    void saveRefreshToken(RefreshTokenRecord record);

    /**
     * 查询用户会话记录列表（按签发时间降序，供管理端会话查询）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID（可空表示不限）
     * @param onlyActive 仅未撤销会话（true 时过滤 revoked=0 且未过期）
     * @param current 当前页码
     * @param size 每页条数
     * @return 会话记录分页结果
     */
    RefreshTokenPage pageRefreshTokens(Long tenantId, Long userId, boolean onlyActive, long current, long size);

    /**
     * 登记 remember-me 令牌记录（预留能力，暂未接入登录流程）
     *
     * @param record 记住我令牌记录
     */
    void appendRememberMeToken(RememberMeTokenRecord record);

    /**
     * 按 series 查询记住我令牌记录
     *
     * @param series 系列标识
     * @return 记住我令牌记录；不存在时返回空
     */
    Optional<RememberMeTokenRecord> findRememberMeBySeries(String series);

    /**
     * 保存记住我令牌记录（轮换 / 撤销标记落库）
     *
     * @param record 记住我令牌记录
     */
    void saveRememberMeToken(RememberMeTokenRecord record);

    /**
     * 登录历史分页结果
     *
     * @param records 当前页记录列表
     * @param total 总记录数
     * @author BlueWhale
     */
    record LoginHistoryPage(List<LoginHistoryRecord> records, long total) {
    }

    /**
     * 会话记录分页结果
     *
     * @param records 当前页记录列表
     * @param total 总记录数
     * @author BlueWhale
     */
    record RefreshTokenPage(List<RefreshTokenRecord> records, long total) {
    }
}
