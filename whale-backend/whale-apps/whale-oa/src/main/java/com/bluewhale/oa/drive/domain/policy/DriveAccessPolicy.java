package com.bluewhale.oa.drive.domain.policy;

import com.bluewhale.oa.drive.domain.model.DriveAcl;
import com.bluewhale.oa.drive.domain.model.DriveFolder;
import com.bluewhale.oa.drive.domain.model.DriveScope;

import java.util.List;

/**
 * 网盘访问策略（纯领域规则，无框架依赖）。
 *
 * <p>优先级：所有者恒可访问；任一匹配主体的 {@code DENY} 优先于 {@code ALLOW}；
 * 部门文件夹对同部门成员默认可读。</p>
 *
 * @author BlueWhale
 */
public final class DriveAccessPolicy {

    /** 授权主体：用户 */
    public static final String SUBJECT_USER = "USER";

    /** 授权主体：部门 */
    public static final String SUBJECT_DEPT = "DEPT";

    /** 效果：允许 */
    public static final String EFFECT_ALLOW = "ALLOW";

    /** 效果：拒绝 */
    public static final String EFFECT_DENY = "DENY";

    /** 权限：只读 */
    public static final String PERMISSION_READ = "READ";

    /** 权限：读写 */
    public static final String PERMISSION_WRITE = "WRITE";

    /** 权限：管理 */
    public static final String PERMISSION_MANAGE = "MANAGE";

    /**
     * 工具类禁止实例化。
     */
    private DriveAccessPolicy() {
    }

    /**
     * 判断是否可读。
     *
     * @param folder 文件夹
     * @param acls   文件夹授权
     * @param userId 当前用户ID
     * @param deptId 当前用户部门ID（可为空）
     * @return 是否可读
     */
    public static boolean canRead(DriveFolder folder, List<DriveAcl> acls, Long userId, Long deptId) {
        if (isOwner(folder, userId)) {
            return true;
        }
        if (hasDeny(acls, userId, deptId)) {
            return false;
        }
        if (isDeptMember(folder, deptId)) {
            return true;
        }
        return hasAllow(acls, userId, deptId);
    }

    /**
     * 判断是否可写（WRITE 或 MANAGE）。
     *
     * @param folder 文件夹
     * @param acls   文件夹授权
     * @param userId 当前用户ID
     * @param deptId 当前用户部门ID（可为空）
     * @return 是否可写
     */
    public static boolean canWrite(DriveFolder folder, List<DriveAcl> acls, Long userId, Long deptId) {
        if (isOwner(folder, userId)) {
            return true;
        }
        if (hasDeny(acls, userId, deptId)) {
            return false;
        }
        return hasAllowWith(acls, userId, deptId, PERMISSION_WRITE) || hasAllowWith(acls, userId, deptId, PERMISSION_MANAGE);
    }

    /**
     * 判断是否可管理（仅 MANAGE）。
     *
     * @param folder 文件夹
     * @param acls   文件夹授权
     * @param userId 当前用户ID
     * @param deptId 当前用户部门ID（可为空）
     * @return 是否可管理
     */
    public static boolean canManage(DriveFolder folder, List<DriveAcl> acls, Long userId, Long deptId) {
        if (isOwner(folder, userId)) {
            return true;
        }
        if (hasDeny(acls, userId, deptId)) {
            return false;
        }
        return hasAllowWith(acls, userId, deptId, PERMISSION_MANAGE);
    }

    /**
     * 权限等级排序值：READ &lt; WRITE &lt; MANAGE。
     *
     * @param permission 权限字符串
     * @return 等级；未知返回 0
     */
    public static int levelOf(String permission) {
        if (PERMISSION_READ.equals(permission)) {
            return 1;
        }
        if (PERMISSION_WRITE.equals(permission)) {
            return 2;
        }
        if (PERMISSION_MANAGE.equals(permission)) {
            return 3;
        }
        return 0;
    }

    /**
     * 判断是否为文件夹所有者。
     *
     * @param folder 文件夹
     * @param userId 当前用户ID
     * @return 是否所有者
     */
    private static boolean isOwner(DriveFolder folder, Long userId) {
        return userId != null && folder.getOwnerId() != null && folder.getOwnerId().equals(userId);
    }

    /**
     * 判断当前用户是否为部门文件夹所属部门成员。
     *
     * @param folder 文件夹
     * @param deptId 当前用户部门ID
     * @return 是否部门成员
     */
    private static boolean isDeptMember(DriveFolder folder, Long deptId) {
        return DriveScope.DEPARTMENT.name().equals(folder.getScope())
                && folder.getDeptId() != null && deptId != null
                && folder.getDeptId().equals(deptId);
    }

    /**
     * 判断是否存在任一匹配主体的拒绝授权。
     *
     * @param acls   授权
     * @param userId 当前用户ID
     * @param deptId 当前用户部门ID
     * @return 是否存在拒绝
     */
    private static boolean hasDeny(List<DriveAcl> acls, Long userId, Long deptId) {
        return acls != null && acls.stream()
                .anyMatch(acl -> matches(acl, userId, deptId) && EFFECT_DENY.equals(acl.getEffect()));
    }

    /**
     * 判断是否存在任一匹配主体的允许授权。
     *
     * @param acls   授权
     * @param userId 当前用户ID
     * @param deptId 当前用户部门ID
     * @return 是否存在允许
     */
    private static boolean hasAllow(List<DriveAcl> acls, Long userId, Long deptId) {
        return acls != null && acls.stream()
                .anyMatch(acl -> matches(acl, userId, deptId) && EFFECT_ALLOW.equals(acl.getEffect()));
    }

    /**
     * 判断是否存在指定权限等级及以上的匹配允许授权。
     *
     * @param acls       授权
     * @param userId     当前用户ID
     * @param deptId     当前用户部门ID
     * @param permission 所需权限
     * @return 是否满足
     */
    private static boolean hasAllowWith(List<DriveAcl> acls, Long userId, Long deptId, String permission) {
        int required = levelOf(permission);
        return acls != null && acls.stream()
                .anyMatch(acl -> matches(acl, userId, deptId)
                        && EFFECT_ALLOW.equals(acl.getEffect())
                        && levelOf(acl.getPermission()) >= required);
    }

    /**
     * 判断授权主体是否匹配当前用户或部门。
     *
     * @param acl    授权
     * @param userId 当前用户ID
     * @param deptId 当前用户部门ID
     * @return 是否匹配
     */
    private static boolean matches(DriveAcl acl, Long userId, Long deptId) {
        if (acl == null || acl.getSubjectId() == null) {
            return false;
        }
        if (SUBJECT_USER.equals(acl.getSubjectType())) {
            return userId != null && acl.getSubjectId().equals(userId);
        }
        if (SUBJECT_DEPT.equals(acl.getSubjectType())) {
            return deptId != null && acl.getSubjectId().equals(deptId);
        }
        return false;
    }
}
