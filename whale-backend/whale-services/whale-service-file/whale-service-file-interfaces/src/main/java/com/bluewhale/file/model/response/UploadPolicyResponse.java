package com.bluewhale.file.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 上传策略视图响应：开关、全局默认上限与当前生效的按权限码规则。
 *
 * <p>{@code enabled} 与全局默认值只读展示，热更新接口不接受这两个字段。</p>
 *
 * @author BlueWhale
 */
@Data
public class UploadPolicyResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否启用按权限码策略（来自启动配置） */
    private Boolean enabled;

    /** 未命中规则时的单文件默认上限（字节） */
    private Long defaultMaxSizeBytes;

    /** 未命中规则时的个人默认配额（字节，{@code <=0} 表示不限） */
    private Long defaultQuotaBytes;

    /** 当前生效的规则集合 */
    private List<Rule> rules;

    /**
     * 单条上传策略规则。
     *
     * @author BlueWhale
     */
    @Data
    public static class Rule implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 需要的权限码 */
        private String permission;

        /** 生效的单文件最大字节数 */
        private Long maxSizeBytes;

        /** 生效的个人配额；{@code null} 表示沿用全局默认配额，{@code <=0} 表示不限 */
        private Long quotaBytes;
    }
}
