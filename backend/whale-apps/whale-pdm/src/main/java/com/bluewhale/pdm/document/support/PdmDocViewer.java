package com.bluewhale.pdm.document.support;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * 图文档访问者身份
 *
 * <p>由网关可信注入的用户 ID 与角色 code 构造，用于 ACL 主体匹配。</p>
 *
 * @param userId 用户 ID
 * @param roles  角色 code 集合
 * @author BlueWhale
 */
public record PdmDocViewer(Long userId, Set<String> roles) {

    /**
     * 从请求构造访问者
     *
     * @param request 当前请求
     * @return 访问者
     */
    public static PdmDocViewer of(HttpServletRequest request) {
        return new PdmDocViewer(PdmDocRequestContext.requireUserId(request),
                PdmDocRequestContext.roles(request));
    }

    /**
     * 是否具备跨文档管理能力
     *
     * @return true 表示可管理他人文档
     */
    public boolean canManage() {
        return PdmDocRoles.canManage(roles);
    }
}
