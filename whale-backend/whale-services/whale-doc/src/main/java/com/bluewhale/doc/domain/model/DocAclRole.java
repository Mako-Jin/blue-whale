package com.bluewhale.doc.domain.model;

/**
 * 页面级权限角色。
 *
 * <p>仅在页面 {@code is_restricted=1} 时生效：READ 可读，EDIT 可读并可编辑。
 * 空间 OWNER/ADMIN 不受 ACL 限制，始终可读写并管理授权。</p>
 *
 * @author BlueWhale
 */
public enum DocAclRole {

    /** 只读 */
    READ(1),

    /** 可编辑 */
    EDIT(2);

    /** 权限层级，数值越大权限越高 */
    private final int rank;

    /**
     * 构造角色。
     *
     * @param rank 权限层级
     */
    DocAclRole(int rank) {
        this.rank = rank;
    }

    /**
     * 判断当前角色是否不低于目标角色。
     *
     * @param required 目标角色
     * @return 当前角色层级大于等于目标角色时返回 {@code true}
     */
    public boolean atLeast(DocAclRole required) {
        return required != null && this.rank >= required.rank;
    }

    /**
     * 解析角色文本，非法值返回 {@code null}。
     *
     * @param value 角色文本
     * @return 角色；非法值返回 {@code null}
     */
    public static DocAclRole parse(String value) {
        try {
            return value == null ? null : valueOf(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
