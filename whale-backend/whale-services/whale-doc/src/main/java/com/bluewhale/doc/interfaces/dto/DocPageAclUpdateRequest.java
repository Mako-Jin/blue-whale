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
    @NotNull(message = "restricted 不能为空")
    private Boolean restricted;

    /** 授权列表；restricted=false 时忽略 */
    @Valid
    @Size(max = 100, message = "授权用户不能超过 100 个")
    private List<Item> items = new ArrayList<>();

    /**
     * 单条授权。
     */
    @Data
    public static class Item {

        /** 用户ID */
        @NotNull(message = "用户ID不能为空")
        private Long userId;

        /** 页面角色：READ / EDIT */
        @Pattern(regexp = "READ|EDIT", message = "页面角色只能是 READ 或 EDIT")
        private String aclRole = "READ";
    }
}
