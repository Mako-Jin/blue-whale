package com.bluewhale.auth.interfaces.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 用户 ABAC 属性写入请求（全量覆盖）
 *
 * @author BlueWhale
 */
@Data
public class UpdateAttrsRequest {

    @NotNull(message = "属性列表不能为 null")
    @Valid
    private List<UserAttrItem> attrs;
}
