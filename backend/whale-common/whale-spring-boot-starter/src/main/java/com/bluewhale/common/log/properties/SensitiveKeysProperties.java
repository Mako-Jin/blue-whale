package com.bluewhale.common.log.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 脱敏规则配置化（AUTH-11，REQ-SEC-006）。
 *
 * <p>{@code whale.audit.sensitive.keys}：附加敏感键名（忽略大小写、去 {@code _}/{@code -} 后匹配），
 * 与 {@code SensitiveDataMasker} 内置规则**并集**生效，命中即整体掩码。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.audit.sensitive")
public class SensitiveKeysProperties {

    /** 附加敏感键名（与内置规则并集，命中整体掩码） */
    private List<String> keys = new ArrayList<>();
}
