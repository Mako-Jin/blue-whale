package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.valueobject.ValueObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * OAuth2 授权范围值对象。
 *
 * <p><b>不变量</b>：至少一个 scope；每个 scope 仅允许字母、数字与 {@code : . _ -}，
 * 便于直接与 {@code sys_permission.perm_code} 前缀做映射。</p>
 *
 * @param values 授权范围列表（去重、保序、不可变）
 * @author BlueWhale
 */
public record ScopeSet(List<String> values) implements ValueObject {

    /** scope 合法字符 */
    private static final Pattern SCOPE = Pattern.compile("^[A-Za-z0-9:._-]+$");

    /**
     * 紧凑构造器：校验非空与字符集
     *
     * @param values 授权范围列表
     */
    public ScopeSet {
        List<String> normalized = new ArrayList<>();
        if (values != null) {
            for (String scope : values) {
                String trimmed = scope == null ? null : scope.trim();
                if (trimmed == null || trimmed.isEmpty() || !SCOPE.matcher(trimmed).matches()) {
                    throw new DomainException(ErrorCode.PARAM_ERROR, "授权范围非法：" + scope);
                }
                if (!normalized.contains(trimmed)) {
                    normalized.add(trimmed);
                }
            }
        }
        if (normalized.isEmpty()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "授权范围不能为空");
        }
        values = List.copyOf(normalized);
    }

    /**
     * 由集合创建授权范围
     *
     * @param values 授权范围集合
     * @return 授权范围值对象
     */
    public static ScopeSet of(Collection<String> values) {
        return new ScopeSet(values == null ? null : List.copyOf(values));
    }

    /**
     * 由逗号分隔文本还原
     *
     * @param text 逗号分隔的授权范围文本
     * @return 授权范围值对象
     */
    public static ScopeSet fromText(String text) {
        List<String> items = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            for (String item : text.split(",")) {
                if (!item.isBlank()) {
                    items.add(item.trim());
                }
            }
        }
        return new ScopeSet(items);
    }

    /**
     * 序列化为逗号分隔文本（落库口径）
     *
     * @return 逗号分隔文本
     */
    public String asText() {
        return String.join(",", values);
    }
}
