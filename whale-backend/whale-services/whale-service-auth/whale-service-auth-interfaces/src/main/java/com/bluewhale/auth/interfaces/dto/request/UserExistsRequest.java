package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量用户存在性校验请求（服务间内网调用）
 *
 * @author BlueWhale
 */
@Data
public class UserExistsRequest {

    /** 单次最多校验的用户数 */
    private static final int MAX_BATCH = 200;

    /** 待校验用户 ID 列表 */
    @NotEmpty(message = "{validation.auth.user.ids-required}")
    @Size(max = MAX_BATCH, message = "{validation.auth.user.ids-size}")
    private List<Long> userIds;
}
