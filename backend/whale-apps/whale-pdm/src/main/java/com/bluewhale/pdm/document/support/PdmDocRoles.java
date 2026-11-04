package com.bluewhale.pdm.document.support;

import java.util.Set;

/**
 * PDM 图文档管理角色判定
 *
 * <p>仅做数据范围兜底：功能权限仍由 {@code @RequirePermission} 校验，
 * 本类用于判断是否可管理他人文档（租户管理员 / PDM 管理员 / 图文档管理员）。</p>
 *
 * @author BlueWhale
 */
public final class PdmDocRoles {

    /** 租户管理员角色 */
    private static final String TENANT_ADMIN = "tenant_admin";

    /** PDM 管理员角色 */
    private static final String PDM_MANAGER = "pdm_manager";

    /** 图文档管理员角色 */
    private static final String PDM_DOC_MANAGER = "pdm_doc_manager";

    /**
     * 工具类禁止实例化
     */
    private PdmDocRoles() {
    }

    /**
     * 是否具备跨文档管理能力
     *
     * @param roles 当前用户角色集合
     * @return true 表示可管理他人文档
     */
    public static boolean canManage(Set<String> roles) {
        return roles.contains(TENANT_ADMIN) || roles.contains(PDM_MANAGER) || roles.contains(PDM_DOC_MANAGER);
    }
}
