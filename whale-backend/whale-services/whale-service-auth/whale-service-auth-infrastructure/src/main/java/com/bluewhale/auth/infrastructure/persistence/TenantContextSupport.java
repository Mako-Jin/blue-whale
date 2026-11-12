package com.bluewhale.auth.infrastructure.persistence;

import com.bluewhale.common.tenant.TenantContext;

import java.util.function.Supplier;

/**
 * 租户上下文作用域工具：临时清空 {@link TenantContext} 执行逻辑，结束后恢复原值（含 null）。
 *
 * <p>用途：读取/写入归属首页租户的数据（如跨租户用户查询、平台级配置表）时绕过
 * MyBatis-Plus 租户行级插件的自动改写。try-finally 封装收敛在此处，避免各仓储
 * 手写 set/restore 的样板与遗漏恢复风险。</p>
 *
 * @author BlueWhale
 */
public final class TenantContextSupport {

    /**
     * 私有构造器：静态工具类禁止实例化
     */
    private TenantContextSupport() {
    }

    /**
     * 清空租户上下文执行有返回值逻辑，结束后恢复原上下文
     *
     * @param action 待执行逻辑
     * @param <T>    返回类型
     * @return 执行结果
     */
    public static <T> T runWithoutTenant(Supplier<T> action) {
        Long previous = TenantContext.get();
        TenantContext.clear();
        try {
            return action.get();
        } finally {
            TenantContext.set(previous);
        }
    }

    /**
     * 清空租户上下文执行无返回值逻辑，结束后恢复原上下文
     *
     * @param action 待执行逻辑
     */
    public static void runWithoutTenant(Runnable action) {
        runWithoutTenant(() -> {
            action.run();
            return null;
        });
    }
}
