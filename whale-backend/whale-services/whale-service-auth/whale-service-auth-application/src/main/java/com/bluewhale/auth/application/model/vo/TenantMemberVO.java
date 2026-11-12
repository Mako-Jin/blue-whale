package com.bluewhale.auth.application.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 租户成员视图（Wave 5.54 Phase 5.3）。
 *
 * <p>成员身份来自 {@code sys_user_tenant} 启用关系，角色只包含当前租户内的角色；
 * 用户基本信息可跨租户读取（账号归属首页租户，切换后令牌租户不同）。</p>
 *
 * @author BlueWhale
 */
@Data
public class TenantMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 全局用户 ID（切换前后不变） */
    private Long userId;
    private String username;
    private String nickname;
    private String phone;
    /** 用户状态：1 启用 / 0 停用（全局生效） */
    private Integer status;
    /** 当前租户内已分配角色 */
    private List<RoleItem> roles;

    /**
     * 成员在当前租户内的角色项
     *
     * @author BlueWhale
     */
    @Data
    public static class RoleItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String roleCode;
        private String roleName;

        /**
         * 构造角色项
         *
         * @param id 角色 ID
         * @param roleCode 角色编码
         * @param roleName 角色名称
         * @return 角色项
         */
        public static RoleItem of(Long id, String roleCode, String roleName) {
            RoleItem item = new RoleItem();
            item.setId(id);
            item.setRoleCode(roleCode);
            item.setRoleName(roleName);
            return item;
        }
    }
}
