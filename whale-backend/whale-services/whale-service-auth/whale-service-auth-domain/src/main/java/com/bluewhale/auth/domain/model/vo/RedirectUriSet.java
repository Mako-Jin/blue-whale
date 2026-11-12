package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.valueobject.ValueObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * OAuth2 回调地址白名单值对象。
 *
 * <p><b>不变量</b>：① 每个回调地址必须是 {@code http(s)://} 绝对地址；② 禁止通配符 {@code *}；
 * ③ 换票时按字符串精确匹配，不做前缀/正则匹配（防止开放重定向）。</p>
 *
 * @param values 回调地址列表（去重、保序、不可变）
 * @author BlueWhale
 */
public record RedirectUriSet(List<String> values) implements ValueObject {

    /** 绝对地址：协议头 + 非空白字符 */
    private static final Pattern ABSOLUTE_URI = Pattern.compile("^https?://\\S+$");

    /**
     * 紧凑构造器：逐项校验并做去重
     *
     * @param values 回调地址列表
     */
    public RedirectUriSet {
        List<String> normalized = new ArrayList<>();
        if (values != null) {
            for (String uri : values) {
                String trimmed = uri == null ? null : uri.trim();
                if (trimmed == null || trimmed.isEmpty()
                        || trimmed.contains("*")
                        || !ABSOLUTE_URI.matcher(trimmed).matches()) {
                    throw new DomainException(ErrorCode.PARAM_ERROR,
                            "回调地址非法：" + uri + "（必须为 http(s) 绝对地址且禁止通配符）");
                }
                if (!normalized.contains(trimmed)) {
                    normalized.add(trimmed);
                }
            }
        }
        values = List.copyOf(normalized);
    }

    /**
     * 由集合创建回调地址白名单
     *
     * @param values 回调地址集合
     * @return 回调地址白名单值对象
     */
    public static RedirectUriSet of(Collection<String> values) {
        return new RedirectUriSet(values == null ? null : List.copyOf(values));
    }

    /**
     * 由逗号分隔文本还原
     *
     * @param text 逗号分隔的回调地址文本（可空）
     * @return 回调地址白名单值对象
     */
    public static RedirectUriSet fromText(String text) {
        return new RedirectUriSet(split(text));
    }

    /**
     * 是否为空白名单
     *
     * @return 空返回 {@code true}
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * 精确匹配回调地址
     *
     * @param uri 待校验回调地址
     * @return 命中白名单返回 {@code true}
     */
    public boolean contains(String uri) {
        return values.contains(uri);
    }

    /**
     * 序列化为逗号分隔文本（落库口径）
     *
     * @return 逗号分隔文本；空白名单返回空串
     */
    public String asText() {
        return String.join(",", values);
    }

    /**
     * 按逗号拆分并去除空白项
     *
     * @param text 逗号分隔文本
     * @return 拆分结果
     */
    private static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String item : text.split(",")) {
            if (!item.isBlank()) {
                items.add(item.trim());
            }
        }
        return items;
    }
}
