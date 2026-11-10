package com.bluewhale.scene.domain.model;

/**
 * 可见性主体类型常量（与 DDL 的 {@code sc_visibility.subject_type} 取值一致）。
 *
 * <p>{@code DEPT} 首期只登记不参与判定：网关注入的身份头不包含部门 ID，
 * 部门粒度后置（决策 D3）。</p>
 *
 * @author BlueWhale
 */
public final class VisibilitySubjectType {

    /** 全体用户 */
    public static final String ALL = "ALL";

    /** 角色 */
    public static final String ROLE = "ROLE";

    /** 部门（首期不参与判定） */
    public static final String DEPT = "DEPT";

    /** 用户 */
    public static final String USER = "USER";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private VisibilitySubjectType() {
    }

    /**
     * 判断主体类型是否合法。
     *
     * @param subjectType 主体类型
     * @return 合法返回 {@code true}
     */
    public static boolean isValid(String subjectType) {
        return ALL.equals(subjectType) || ROLE.equals(subjectType)
                || DEPT.equals(subjectType) || USER.equals(subjectType);
    }
}
