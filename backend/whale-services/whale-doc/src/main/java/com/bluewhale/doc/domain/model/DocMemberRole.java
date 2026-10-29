package com.bluewhale.doc.domain.model;

/**
 * 文档空间成员角色。
 *
 * <p>角色按权限从高到低排列，{@link #atLeast(DocMemberRole)} 用于数据权限二次校验；
 * 平台权限码只控制功能入口，具体编辑/发布/删除/成员管理由空间角色决定。</p>
 *
 * @author BlueWhale
 */
public enum DocMemberRole {

    /** 空间所有者：唯一，可转让，拥有全部空间权限 */
    OWNER(4),

    /** 空间管理员：成员管理、页面删除 */
    ADMIN(3),

    /** 编辑者：新建/编辑/发布/移动页面、管理附件 */
    EDITOR(2),

    /** 只读者：读取成员空间与评论 */
    READER(1);

    /** 权限层级，数值越大权限越高 */
    private final int rank;

    /**
     * 构造角色。
     *
     * @param rank 权限层级
     */
    DocMemberRole(int rank) {
        this.rank = rank;
    }

    /**
     * 判断当前角色是否不低于目标角色。
     *
     * @param required 目标角色
     * @return 当前角色层级大于等于目标角色时返回 {@code true}
     */
    public boolean atLeast(DocMemberRole required) {
        return required != null && this.rank >= required.rank;
    }
}
