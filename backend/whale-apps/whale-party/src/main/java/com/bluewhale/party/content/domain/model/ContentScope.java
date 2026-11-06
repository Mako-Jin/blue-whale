package com.bluewhale.party.content.domain.model;

import java.util.List;

/**
 * 阅读可见范围（由操作人身份换算而来）。
 *
 * <p>这是<b>读者侧</b>的范围，不是内容上的 {@link ContentVisibility}：内容声明「发给谁」，
 * 本对象表示「当前这个人能看到哪几类」。审核人拿 {@link #manager()}，
 * 不受内容可见范围限制——否则审核人看不到自己通过的内容，无法复核。</p>
 *
 * @author BlueWhale
 */
public final class ContentScope {

    /** 是否审核人（可看全部已发布内容） */
    private final boolean manager;

    /** 是否具备党员身份（可看「全体党员」内容） */
    private final boolean allMembers;

    /** 本人所属支部 ID 列表 */
    private final List<Long> branchIds;

    /**
     * 构造范围。
     *
     * @param manager 是否审核人
     * @param allMembers 是否党员
     * @param branchIds 所属支部
     */
    private ContentScope(boolean manager, boolean allMembers, List<Long> branchIds) {
        this.manager = manager;
        this.allMembers = allMembers;
        this.branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
    }

    /**
     * 匿名 / 无任何党务身份：只能看全员内容。
     *
     * @return 范围
     */
    public static ContentScope none() {
        return new ContentScope(false, false, List.of());
    }

    /**
     * 审核人范围：全部已发布内容可见。
     *
     * @return 范围
     */
    public static ContentScope manager() {
        return new ContentScope(true, true, List.of());
    }

    /**
     * 普通读者范围。
     *
     * @param allMembers 是否具备党员身份
     * @param branchIds 所属支部
     * @return 范围
     */
    public static ContentScope of(boolean allMembers, List<Long> branchIds) {
        return new ContentScope(false, allMembers, branchIds);
    }

    /**
     * 是否审核人。
     *
     * @return 审核人返回 true
     */
    public boolean isManager() {
        return manager;
    }

    /**
     * 是否具备党员身份。
     *
     * @return 党员返回 true
     */
    public boolean isAllMembers() {
        return allMembers;
    }

    /**
     * 查询所属支部。
     *
     * @return 支部列表
     */
    public List<Long> getBranchIds() {
        return branchIds;
    }

    /**
     * 内容声明的支部是否命中本人所属支部。
     *
     * @param contentBranchIds 内容指定支部
     * @return 命中返回 true；审核人恒为 true
     */
    public boolean coversBranches(List<Long> contentBranchIds) {
        if (manager) {
            return true;
        }
        if (contentBranchIds == null || contentBranchIds.isEmpty()) {
            return false;
        }
        for (Long branchId : contentBranchIds) {
            if (branchId != null && branchIds.contains(branchId)) {
                return true;
            }
        }
        return false;
    }
}
