package com.bluewhale.file.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 上传策略热更新请求：整表替换按权限码的规则。
 *
 * <p>只接受规则集合，开关与全局默认上限不在请求体内，防止借热更新绕过启动配置。</p>
 *
 * @author BlueWhale
 */
@Data
public class UploadPolicyUpdateRequest {

    /** 新规则集合：整体替换，至少一条 */
    @NotEmpty(message = "{validation.file.upload-policy.rules-required}")
    private List<@Valid @NotNull(message = "{validation.file.upload-policy.rule-entry-required}") Rule> rules;

    /**
     * 单条上传策略规则。
     *
     * @author BlueWhale
     */
    @Data
    public static class Rule {

        /** 需要的权限码 */
        @NotBlank(message = "{validation.file.upload-policy.permission-required}")
        private String permission;

        /** 生效的单文件最大字节数 */
        @Min(value = 1, message = "{validation.file.upload-policy.max-size-positive}")
        private long maxSizeBytes;

        /** 生效的个人配额；缺省表示沿用全局默认配额，{@code 0} 表示不限 */
        @Min(value = 0, message = "{validation.file.upload-policy.quota-non-negative}")
        private Long quotaBytes;
    }
}
