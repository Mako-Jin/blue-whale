package com.bluewhale.map.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.map.config.MapProperties;
import com.bluewhale.map.entity.MapQuotaUsage;
import com.bluewhale.map.mapper.MapQuotaUsageMapper;
import com.bluewhale.map.service.MapQuotaService;
import com.bluewhale.map.vo.ProviderHealthVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 配额服务实现。
 *
 * <p><b>兜底口径</b>：数据库异常时放行。理由是这个方法在每次外呼前被调用，
 * 如果计数表不可用就拒绝服务，等于把「统计功能」升级成「单点故障」；
 * 而放行的后果只是可能多打几次接口，供应商自己也会兜一层限流。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MapQuotaServiceImpl implements MapQuotaService {

    /** 不限额度标识 */
    private static final int UNLIMITED = 0;

    /** 剩余量「不限」的返回值 */
    private static final int REMAINING_UNLIMITED = -1;

    /** 配额用量 Mapper */
    private final MapQuotaUsageMapper quotaMapper;

    /** 服务配置 */
    private final MapProperties properties;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryAcquire(String provider) {
        Long tenantId = TenantContext.requireId();
        LocalDate today = LocalDate.now();
        try {
            ensureRow(tenantId, provider, today);
            return quotaMapper.tryConsume(tenantId, provider, today) > 0;
        } catch (RuntimeException ex) {
            log.warn("配额计数失败，本次放行：provider={}，原因={}", provider, ex.getMessage());
            return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProviderHealthVO quotaOf(String provider) {
        ProviderHealthVO view = new ProviderHealthVO();
        view.setName(provider);
        MapQuotaUsage usage = findRow(provider);
        int used = usage == null ? 0 : usage.getUsedCount();
        int limit = usage == null ? limitOf(provider) : usage.getLimitCount();
        view.setUsedToday(used);
        view.setLimitToday(limit);
        view.setRemaining(limit == UNLIMITED ? REMAINING_UNLIMITED : Math.max(limit - used, 0));
        return view;
    }

    /**
     * 查询今日用量行
     *
     * @param provider 供应商名
     * @return 用量行；不存在返回 {@code null}
     */
    private MapQuotaUsage findRow(String provider) {
        LambdaQueryWrapper<MapQuotaUsage> wrapper = new LambdaQueryWrapper<MapQuotaUsage>()
                .eq(MapQuotaUsage::getProvider, provider)
                .eq(MapQuotaUsage::getUsageDate, LocalDate.now())
                .last("LIMIT 1");
        return quotaMapper.selectOne(wrapper);
    }

    /**
     * 确保今日用量行存在
     *
     * @param tenantId 租户ID
     * @param provider 供应商名
     * @param today    统计日期
     */
    private void ensureRow(Long tenantId, String provider, LocalDate today) {
        if (findRow(provider) != null) {
            return;
        }
        MapQuotaUsage row = new MapQuotaUsage();
        row.setProvider(provider);
        row.setUsageDate(today);
        row.setUsedCount(0);
        row.setLimitCount(limitOf(provider));
        try {
            quotaMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            // 并发下另一个请求已经插入：唯一键就是这里的互斥手段，冲突即代表成功
            log.debug("配额行已由并发请求创建：tenant={}，provider={}，date={}", tenantId, provider, today);
        }
    }

    /**
     * 读取供应商配置的日配额
     *
     * @param provider 供应商名
     * @return 日配额（0 表示不限）
     */
    private int limitOf(String provider) {
        MapProperties.ProviderConfig config = properties.getProviders().get(provider);
        return config == null ? UNLIMITED : config.getQuotaPerDay();
    }
}
