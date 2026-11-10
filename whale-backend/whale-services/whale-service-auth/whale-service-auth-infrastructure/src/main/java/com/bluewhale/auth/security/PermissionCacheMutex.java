package com.bluewhale.auth.security;

import com.bluewhale.common.lock.annotation.DistributedLock;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 权限缓存重建互斥门面（REQ-DIST-002 / REQ-DIST-003 防击穿）。
 * <p>唯一方法 {@link #load(Long, Supplier)} 携带 {@code @DistributedLock}，锁 Key 为
 * {@code lock:cache:auth:user:perm:{userId}}，使同一用户的缓存重建串行化。</p>
 * <p><b>独立 Bean</b>：{@link PermissionCache} 通过跨 Bean 调用本类，规避「同类自调用不走 AOP」的切面失效陷阱。</p>
 *
 * @author BlueWhale
 */
@Component
public class PermissionCacheMutex {

    /**
     * 在分布式锁保护下执行回源加载（透传，不改变加载语义）
     * <p><b>为何显式 {@code failFast = false}（全仓唯一允许的放行点）</b>：本方法只是
     * 「缓存重建互斥」，抢不到锁说明另一实例正在重建同一用户的权限缓存；此时代码继续
     * 执行 loader——loader 内部会再读一次缓存，读不到才回源，最坏后果是并发多查一次库，
     * 不存在状态被并发写坏的可能。因此放行是刻意的降级语义，而非遗漏：
     * 若改为 {@code failFast = true}，高并发下会退化为大量 1s 抢锁失败的业务异常。</p>
     * <p>约束：除本处外不得再出现 {@code failFast = false}（{@code AuthArchitectureTest}
     * 已加守护规则），确有非幂等副作用的方法必须保持注解默认的快速失败。</p>
     *
     * @param userId 用户 ID，作为锁 Key 的业务标识
     * @param loader 回源加载器（通常内部再做一次缓存复查，命中则直接返回）
     * @return 加载器返回值
     */
    @DistributedLock(prefix = "lock", biz = "cache:auth:user:perm", key = "#userId",
            waitTime = 1, leaseTime = 10, failFast = false)
    public String load(Long userId, Supplier<String> loader) {
        return loader.get();
    }
}
