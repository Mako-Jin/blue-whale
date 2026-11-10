package com.bluewhale.scene.domain.model;

import lombok.Data;

/**
 * 节点入口绑定。
 *
 * <p>M2 采用「一个节点至多一个入口」的简化模型（DDL 唯一键 {@code tenant_id + node_id}），
 * 入口地址与权限码只做跳转与可见过滤，不复制业务系统数据。</p>
 *
 * @author BlueWhale
 */
@Data
public class NodeBinding {

    /** 主键 */
    private Long id;

    /** 关联节点 ID */
    private Long nodeId;

    /** 入口类型：SYSTEM / PAGE / KNOWLEDGE / EXTERNAL */
    private String entryType;

    /** 目标地址：平台内路径（以 / 开头）或白名单协议的绝对地址 */
    private String targetPath;

    /** 访问入口所需的权限码，可为空表示登录即可 */
    private String permissionCode;

    /** 是否启用 */
    private Boolean enabled;

    /**
     * 判断入口是否对指定权限集合可见。
     *
     * @param permissions 当前用户权限码集合
     * @return 启用且权限满足返回 {@code true}
     */
    public boolean visibleTo(java.util.Set<String> permissions) {
        if (Boolean.FALSE.equals(enabled)) {
            return false;
        }
        return permissionCode == null || permissionCode.isBlank() || permissions.contains(permissionCode);
    }
}
