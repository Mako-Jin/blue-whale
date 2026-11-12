package com.bluewhale.auth.application.model.command;

import java.util.List;

/**
 * 用户 ABAC 属性全量覆盖命令（AUTH-17 / T-DDD-9）
 *
 * <p>先删后批量插，{@code attrs} 为 {@code null} 或空时仅清空用户既有属性。</p>
 *
 * @param attrs 目标属性列表，可为 {@code null} 或空
 * @author BlueWhale
 */
public record ReplaceUserAttrsCommand(List<UserAttrCommand> attrs) {

    /**
     * 单条用户属性命令
     *
     * @param permissionId 权限点 ID
     * @param attrKey 属性键
     * @param attrValue 属性值
     * @author BlueWhale
     */
    public record UserAttrCommand(Long permissionId, String attrKey, String attrValue) {
    }
}
