package com.bluewhale.agent.support.knowledge;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 知识检索权限上下文：问答线程内透传当前用户权限码，供索引层过滤受限知识块。
 *
 * <p>仅在显式设置后生效；未设置时按空集合处理（仅可见无权限码约束的普通知识块），
 * 避免内部/调试链路默认放开档案知识。</p>
 *
 * @author BlueWhale
 */
public final class KnowledgePermissionContext {

    private static final ThreadLocal<Set<String>> HOLDER = new ThreadLocal<>();

    /** 工具类不允许实例化 */
    private KnowledgePermissionContext() {
    }

    /**
     * 设置当前线程权限码。
     *
     * @param permissionCodes 权限码集合（可为 {@code null}）
     */
    public static void set(Set<String> permissionCodes) {
        HOLDER.set(permissionCodes == null ? null : Collections.unmodifiableSet(
                new LinkedHashSet<>(permissionCodes)));
    }

    /**
     * 读取当前线程权限码。
     *
     * @return 权限码集合；未设置返回空集合（仅普通知识块可见）
     */
    public static Set<String> current() {
        Set<String> codes = HOLDER.get();
        return codes == null ? Collections.emptySet() : codes;
    }

    /**
     * 清理当前线程权限码（必须在回答结束时调用，避免线程复用串权限）。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
