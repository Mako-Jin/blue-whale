package com.bluewhale.auth.application.tenant;

import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import com.bluewhale.auth.domain.tenant.repository.TenantRepository;
import com.bluewhale.security.token.TenantStatusKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 租户状态缓存写入器（Wave 5.54 Phase 2）。
 *
 * <p>租户开通、启停时写入 {@code whale:{tenantId}:cache:auth:tenant:status}，供网关即时
 * 拒绝停用租户的存量 accessToken；应用启动时按数据库现状全量预热一次。</p>
 *
 * <p>Redis 不可用时只记录告警，不影响租户管理主流程（网关侧按启用降级放行）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantStatusCache implements ApplicationRunner {

    private final TenantRepository tenantRepository;

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    /**
     * 同步单个租户状态到缓存
     *
     * @param tenant 租户聚合
     */
    public void sync(Tenant tenant) {
        if (tenant == null || tenant.getId() == null) {
            return;
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        String value = tenant.isEnabled() ? TenantStatusKeys.ENABLED : TenantStatusKeys.DISABLED;
        try {
            redisTemplate.opsForValue().set(TenantStatusKeys.statusKey(tenant.getId()), value);
        } catch (Exception e) {
            log.warn("租户状态缓存写入失败: tenantId={}, reason={}", tenant.getId(), e.getMessage());
        }
    }

    /**
     * 应用启动预热：把数据库中的租户状态全量写入缓存
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (redisTemplateProvider.getIfAvailable() == null) {
            return;
        }
        tenantRepository.findAll().forEach(this::sync);
        log.info("租户状态缓存预热完成");
    }
}
