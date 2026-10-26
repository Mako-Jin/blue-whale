package com.bluewhale.auth.dto;

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
    @NotEmpty(message = "用户ID列表不能为空")
    @Size(max = MAX_BATCH, message = "单次最多校验 200 个用户")
    private List<Long> userIds;
}
