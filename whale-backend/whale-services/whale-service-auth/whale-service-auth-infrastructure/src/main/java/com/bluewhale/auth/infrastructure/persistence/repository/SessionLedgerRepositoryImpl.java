package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.model.entity.DeviceRecord;
import com.bluewhale.auth.domain.model.entity.LoginHistoryRecord;
import com.bluewhale.auth.domain.model.entity.RefreshTokenRecord;
import com.bluewhale.auth.domain.model.entity.RememberMeTokenRecord;
import com.bluewhale.auth.domain.repository.SessionLedgerRepository;
import com.bluewhale.auth.infrastructure.persistence.assembler.SessionLedgerPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysDeviceMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysLoginHistoryMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysRefreshTokenMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysRememberMeTokenMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysDevicePo;
import com.bluewhale.auth.infrastructure.persistence.po.SysLoginHistoryPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysRefreshTokenPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysRememberMeTokenPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 会话设备仓储 MyBatis-Plus 实现（权限中心扩展 M3）。
 *
 * <p>设备表逻辑删除由 {@code deleted} 列承担；登录历史 / refreshToken / remember-me
 * 三张表建表即无 {@code deleted} 与（登录历史亦无 {@code update_time}）列，
 * 与 PO 一致，不支持 UPDATE/DELETE。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SessionLedgerRepositoryImpl implements SessionLedgerRepository {

    private final SysDeviceMapper deviceMapper;
    private final SysLoginHistoryMapper loginHistoryMapper;
    private final SysRefreshTokenMapper refreshTokenMapper;
    private final SysRememberMeTokenMapper rememberMeTokenMapper;

    /**
     * 追加登录历史（append-only）
     *
     * @param record 登录历史记录
     */
    @Override
    public void appendLoginHistory(LoginHistoryRecord record) {
        loginHistoryMapper.insert(SessionLedgerPersistenceAssembler.toPo(record));
    }

    /**
     * 分页查询登录历史（tenant_id 必带，按 loginTime 降序）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID（可空表示不限）
     * @param result 结果过滤（可空表示不限）
     * @param startTime 起始时间（可空）
     * @param endTime 截止时间（可空）
     * @param current 当前页码
     * @param size 每页条数
     * @return 登录历史分页结果
     */
    @Override
    public LoginHistoryPage pageLoginHistory(Long tenantId, Long userId, String result,
                                             LocalDateTime startTime, LocalDateTime endTime,
                                             long current, long size) {
        LambdaQueryWrapper<SysLoginHistoryPo> wrapper = new LambdaQueryWrapper<SysLoginHistoryPo>()
                .eq(SysLoginHistoryPo::getTenantId, tenantId);
        if (userId != null) {
            wrapper.eq(SysLoginHistoryPo::getUserId, userId);
        }
        if (StringUtils.hasText(result)) {
            wrapper.eq(SysLoginHistoryPo::getLoginResult, result);
        }
        if (startTime != null) {
            wrapper.ge(SysLoginHistoryPo::getLoginTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(SysLoginHistoryPo::getLoginTime, endTime);
        }
        wrapper.orderByDesc(SysLoginHistoryPo::getLoginTime);
        Page<SysLoginHistoryPo> page = loginHistoryMapper.selectPage(Page.of(current, size), wrapper);
        List<LoginHistoryRecord> records = page.getRecords().stream()
                .map(SessionLedgerPersistenceAssembler::toLoginHistory).toList();
        return new LoginHistoryPage(records, page.getTotal());
    }

    /**
     * 按（租户, 用户, 指纹）查询设备：命中则刷新快照与最近使用时间，未命中则登记新设备
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param deviceFingerprint 设备指纹
     * @param deviceName 设备名称（可空）
     * @param deviceType 设备类型（可空）
     * @param os 操作系统快照（可空）
     * @param browser 浏览器快照（可空）
     * @return 设备实体
     */
    @Override
    public DeviceRecord upsertDevice(Long tenantId, Long userId, String deviceFingerprint, String deviceName,
                                     String deviceType, String os, String browser) {
        SysDevicePo existing = deviceMapper.selectOne(new LambdaQueryWrapper<SysDevicePo>()
                .eq(SysDevicePo::getTenantId, tenantId)
                .eq(SysDevicePo::getUserId, userId)
                .eq(SysDevicePo::getDeviceFingerprint, deviceFingerprint));
        if (existing != null) {
            DeviceRecord device = SessionLedgerPersistenceAssembler.toDevice(existing);
            device.recordUse(deviceName, deviceType, os, browser);
            return saveDevice(device);
        }
        return saveDevice(DeviceRecord.create(tenantId, userId, deviceFingerprint,
                deviceName, deviceType, os, browser));
    }

    /**
     * 按主键查询设备
     *
     * @param id 设备 ID
     * @return 设备实体；不存在时返回空
     */
    @Override
    public Optional<DeviceRecord> findDeviceById(Long id) {
        SysDevicePo po = deviceMapper.selectById(id);
        return Optional.ofNullable(po).map(SessionLedgerPersistenceAssembler::toDevice);
    }

    /**
     * 查询用户设备列表（按最近使用时间降序）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 设备列表
     */
    @Override
    public List<DeviceRecord> listDevicesByUser(Long tenantId, Long userId) {
        return deviceMapper.selectList(new LambdaQueryWrapper<SysDevicePo>()
                        .eq(SysDevicePo::getTenantId, tenantId)
                        .eq(SysDevicePo::getUserId, userId)
                        .orderByDesc(SysDevicePo::getLastLoginTime))
                .stream().map(SessionLedgerPersistenceAssembler::toDevice).toList();
    }

    /**
     * 保存设备实体：无主键新增（回填 ID），有主键按 ID 更新
     *
     * @param device 设备实体
     * @return 回填主键后的实体
     */
    @Override
    public DeviceRecord saveDevice(DeviceRecord device) {
        SysDevicePo po = SessionLedgerPersistenceAssembler.toPo(device);
        if (po.getId() == null) {
            deviceMapper.insert(po);
        } else {
            deviceMapper.updateById(po);
        }
        return SessionLedgerPersistenceAssembler.toDevice(po);
    }

    /**
     * 登记 refreshToken 会话记录（登录 / 刷新换发时写入）
     *
     * @param record 会话记录
     */
    @Override
    public void appendRefreshToken(RefreshTokenRecord record) {
        refreshTokenMapper.insert(SessionLedgerPersistenceAssembler.toPo(record));
    }

    /**
     * 按 jti 查询会话记录
     *
     * @param jti 令牌唯一标识
     * @return 会话记录；不存在时返回空
     */
    @Override
    public Optional<RefreshTokenRecord> findRefreshTokenByJti(String jti) {
        SysRefreshTokenPo po = refreshTokenMapper.selectOne(new LambdaQueryWrapper<SysRefreshTokenPo>()
                .eq(SysRefreshTokenPo::getJti, jti));
        return Optional.ofNullable(po).map(SessionLedgerPersistenceAssembler::toRefreshToken);
    }

    /**
     * 保存会话记录（撤销标记落库）
     *
     * @param record 会话记录
     */
    @Override
    public void saveRefreshToken(RefreshTokenRecord record) {
        SysRefreshTokenPo po = SessionLedgerPersistenceAssembler.toPo(record);
        if (po.getId() == null) {
            refreshTokenMapper.insert(po);
        } else {
            refreshTokenMapper.updateById(po);
        }
    }

    /**
     * 查询用户会话记录列表（按签发时间降序；onlyActive 时过滤 revoked=0 且未过期）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID（可空表示不限）
     * @param onlyActive 仅未撤销会话
     * @param current 当前页码
     * @param size 每页条数
     * @return 会话记录分页结果
     */
    @Override
    public RefreshTokenPage pageRefreshTokens(Long tenantId, Long userId, boolean onlyActive,
                                              long current, long size) {
        LambdaQueryWrapper<SysRefreshTokenPo> wrapper = new LambdaQueryWrapper<SysRefreshTokenPo>()
                .eq(SysRefreshTokenPo::getTenantId, tenantId);
        if (userId != null) {
            wrapper.eq(SysRefreshTokenPo::getUserId, userId);
        }
        if (onlyActive) {
            wrapper.eq(SysRefreshTokenPo::getRevoked, 0)
                    .gt(SysRefreshTokenPo::getExpiresAt, LocalDateTime.now());
        }
        wrapper.orderByDesc(SysRefreshTokenPo::getIssuedAt);
        Page<SysRefreshTokenPo> page = refreshTokenMapper.selectPage(Page.of(current, size), wrapper);
        List<RefreshTokenRecord> records = page.getRecords().stream()
                .map(SessionLedgerPersistenceAssembler::toRefreshToken).toList();
        return new RefreshTokenPage(records, page.getTotal());
    }

    /**
     * 登记 remember-me 令牌记录
     *
     * @param record 记住我令牌记录
     */
    @Override
    public void appendRememberMeToken(RememberMeTokenRecord record) {
        rememberMeTokenMapper.insert(SessionLedgerPersistenceAssembler.toPo(record));
    }

    /**
     * 按 series 查询记住我令牌记录
     *
     * @param series 系列标识
     * @return 记住我令牌记录；不存在时返回空
     */
    @Override
    public Optional<RememberMeTokenRecord> findRememberMeBySeries(String series) {
        SysRememberMeTokenPo po = rememberMeTokenMapper.selectOne(new LambdaQueryWrapper<SysRememberMeTokenPo>()
                .eq(SysRememberMeTokenPo::getSeries, series));
        return Optional.ofNullable(po).map(SessionLedgerPersistenceAssembler::toRememberMeToken);
    }

    /**
     * 保存记住我令牌记录：无主键新增（回填 ID），有主键按 ID 更新
     *
     * @param record 记住我令牌记录
     */
    @Override
    public void saveRememberMeToken(RememberMeTokenRecord record) {
        SysRememberMeTokenPo po = SessionLedgerPersistenceAssembler.toPo(record);
        if (po.getId() == null) {
            rememberMeTokenMapper.insert(po);
        } else {
            rememberMeTokenMapper.updateById(po);
        }
    }
}
