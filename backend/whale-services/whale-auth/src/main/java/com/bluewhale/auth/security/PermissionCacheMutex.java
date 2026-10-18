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
