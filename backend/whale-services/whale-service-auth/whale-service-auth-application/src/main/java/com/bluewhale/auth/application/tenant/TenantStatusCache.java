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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.ZoneId;

/**
 * 租户状态缓存写入器（Wave 5.54 Phase 2）。
 *
 * <p>租户开通、启停、续期时写入 {@code whale:{tenantId}:cache:auth:tenant:status} 与
 * {@code whale:{tenantId}:cache:auth:tenant:expire}（Unix 秒，缺失表示长期有效），供网关
 * 即时拒绝停用或已到期租户的存量 accessToken；应用启动时按数据库现状全量预热一次。</p>
 *
 * <p>Redis 不可用时只记录告警，不影响租户管理主流程（网关侧按可用降级放行）。</p>
 *
 * <p>事务内调用时写入延迟到提交之后（afterCommit）：租户启停/续期/开通的事务若回滚，
 * 缓存不会领先于数据库，避免误停或误放行；无事务上下文（如启动预热）时立即写入。</p>
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
     * 同步单个租户状态到缓存（事务内延迟到提交后执行）
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
        Long tenantId = tenant.getId();
        String status = tenant.isEnabled() ? TenantStatusKeys.ENABLED : TenantStatusKeys.DISABLED;
        String expireAt = toExpireAtSeconds(tenant);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write(redisTemplate, tenantId, status, expireAt);
                }
            });
            return;
        }
        write(redisTemplate, tenantId, status, expireAt);
    }

    /**
     * 把到期时间换算为 Unix 秒文本
     *
     * @param tenant 租户聚合
     * @return 到期秒数；长期有效返回 {@code null}
     */
    private static String toExpireAtSeconds(Tenant tenant) {
        if (tenant.getExpireTime() == null) {
            return null;
        }
        return String.valueOf(tenant.getExpireTime().atZone(ZoneId.systemDefault()).toEpochSecond());
    }

    /**
     * 写入状态键与到期键：长期有效（到期时间为空）时删除到期键
     *
     * @param redisTemplate 字符串 Redis 模板
     * @param tenantId 租户 ID
     * @param status 状态值
     * @param expireAt 到期 Unix 秒文本，{@code null} 表示长期有效
     */
    private void write(StringRedisTemplate redisTemplate, Long tenantId, String status, String expireAt) {
        try {
            redisTemplate.opsForValue().set(TenantStatusKeys.statusKey(tenantId), status);
            if (expireAt == null) {
                redisTemplate.delete(TenantStatusKeys.expireKey(tenantId));
                return;
            }
            redisTemplate.opsForValue().set(TenantStatusKeys.expireKey(tenantId), expireAt);
        } catch (Exception e) {
            log.warn("租户状态缓存写入失败: tenantId={}, reason={}", tenantId, e.getMessage());
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
