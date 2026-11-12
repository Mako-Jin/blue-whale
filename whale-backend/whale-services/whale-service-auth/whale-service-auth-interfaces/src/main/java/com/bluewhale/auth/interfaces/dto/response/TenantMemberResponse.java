package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 租户成员响应（Wave 5.54 Phase 5.3）：启用关系成员 + 本租户角色
 *
 * @author BlueWhale
 */
@Data
public class TenantMemberResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 全局用户 ID（切换前后不变） */
    private Long userId;
    private String username;
    private String nickname;
    private String phone;
    /** 用户状态：1 启用 / 0 停用（全局生效） */
    private Integer status;
    /** 当前租户内已分配角色 */
    private List<RoleItemResponse> roles;

    /**
     * 成员在当前租户内的角色项
     *
     * @author BlueWhale
     */
    @Data
    public static class RoleItemResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String roleCode;
        private String roleName;
    }
}
