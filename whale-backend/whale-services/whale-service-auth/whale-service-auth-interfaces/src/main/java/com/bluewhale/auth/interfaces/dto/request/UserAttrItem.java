package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户 ABAC 属性项
 *
 * @author BlueWhale
 */
@Data
public class UserAttrItem {

    @NotNull(message = "{validation.auth.user.permission-id-required}")
    private Long permissionId;

    @NotBlank(message = "{validation.auth.user.attr-key-required}")
    private String attrKey;

    @NotBlank(message = "{validation.auth.user.attr-value-required}")
    private String attrValue;
}
