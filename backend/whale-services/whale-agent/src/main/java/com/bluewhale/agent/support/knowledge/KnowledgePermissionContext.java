package com.bluewhale.agent.support.knowledge;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 知识检索访问上下文：问答线程内透传当前用户的权限码与用户ID，供索引层过滤受限/私有知识块。
 *
 * <p>仅在显式设置后生效；未设置时按空集合 + 空用户处理（仅可见无权限码约束、
 * 无归属人约束的公共知识块），避免内部/调试链路默认放开档案知识与个人文件。</p>
 *
 * @author BlueWhale
 */
public final class KnowledgePermissionContext {

    private static final ThreadLocal<Set<String>> HOLDER = new ThreadLocal<>();

    /** 当前用户ID（文件知识归属人过滤依据） */
    private static final ThreadLocal<Long> OWNER_HOLDER = new ThreadLocal<>();

    /** 工具类不允许实例化 */
    private KnowledgePermissionContext() {
    }

    /**
     * 设置当前线程权限码（不指定归属人）。
     *
     * @param permissionCodes 权限码集合（可为 {@code null}）
     */
    public static void set(Set<String> permissionCodes) {
        set(permissionCodes, null);
    }

    /**
     * 设置当前线程权限码与归属人。
     *
     * @param permissionCodes 权限码集合（可为 {@code null}）
     * @param ownerId 当前用户ID（可为 {@code null}，此时仅公共文件知识可见）
     */
    public static void set(Set<String> permissionCodes, Long ownerId) {
        HOLDER.set(permissionCodes == null ? null : Collections.unmodifiableSet(
                new LinkedHashSet<>(permissionCodes)));
        OWNER_HOLDER.set(ownerId);
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
     * 读取当前线程归属人（用户ID）。
     *
     * @return 用户ID；未设置返回 {@code null}（仅无归属人的公共知识块可见）
     */
    public static Long currentOwner() {
        return OWNER_HOLDER.get();
    }

    /**
     * 清理当前线程上下文（必须在回答结束时调用，避免线程复用串权限/串用户）。
     */
    public static void clear() {
        HOLDER.remove();
        OWNER_HOLDER.remove();
    }
}
