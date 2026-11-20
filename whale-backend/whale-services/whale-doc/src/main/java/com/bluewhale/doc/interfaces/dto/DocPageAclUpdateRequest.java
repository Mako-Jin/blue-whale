package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 更新页面级权限请求。
 *
 * @author BlueWhale
 */
@Data
public class DocPageAclUpdateRequest {

    /** 是否启用页面级权限 */
    @NotNull(message = "{validation.doc.permission.restricted-required}")
    private Boolean restricted;

    /** 授权列表；restricted=false 时忽略 */
    @Valid
    @Size(max = 100, message = "{validation.doc.permission.items-size}")
    private List<Item> items = new ArrayList<>();

    /**
     * 单条授权。
     */
    @Data
    public static class Item {

        /** 用户ID */
        @NotNull(message = "{validation.doc.permission.user-id-required}")
        private Long userId;

        /** 页面角色：READ / EDIT */
        @Pattern(regexp = "READ|EDIT", message = "{validation.doc.permission.role-pattern}")
        private String aclRole = "READ";
    }
}
