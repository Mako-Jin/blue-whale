package com.bluewhale.common.audit.support;

import com.bluewhale.common.audit.annotation.Sensitive;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 敏感字段脱敏器：把待落库的对象转换为「Map / List / 标量」结构并递归掩码，
 * 再由 Jackson 序列化为 {@code request_params} 的 JSON 文本。
 *
 * <p>设计要点：</p>
 * <ol>
 *   <li><b>按字段名匹配规则</b>（忽略大小写，去掉 {@code _} / {@code -}），覆盖嵌套对象与数组元素；</li>
 *   <li><b>只依赖 Jackson 的 {@code writeValueAsString}</b>，不触碰 Jackson 3 与 2 存在差异的树模型 API；</li>
 *   <li>递归深度上限 {@value #MAX_DEPTH}，防止循环引用；{@code java.*} / {@code jakarta.*} /
 *       {@code org.springframework.*} 等基础设施类不做递归展开（避免遍历 Servlet 请求对象）；</li>
 *   <li>参数 JSON 超长时按 {@value #MAX_PARAM_LENGTH} 字符截断，控制落库体积。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Slf4j
public class SensitiveDataMasker {

    /** 密码类字段（整体掩码，永不落明文/密文） */
    private static final Set<String> SECRET_KEYS =
            Set.of("password", "oldpassword", "newpassword", "confirmpassword");
    /** 令牌类字段（整体掩码） */
    private static final Set<String> TOKEN_KEYS =
            Set.of("token", "authorization", "accesstoken", "refreshtoken");
    /** 手机号类字段（保留前 3 后 4） */
    private static final Set<String> MOBILE_KEYS = Set.of("phone", "mobile", "telephone");
    /** 身份证类字段（保留前 6 后 4） */
    private static final Set<String> ID_CARD_KEYS = Set.of("idcard", "idno", "identitycard");
    /** 银行卡类字段（仅保留后 4 位） */
    private static final Set<String> BANK_CARD_KEYS = Set.of("bankcard", "cardno", "bankcardno");
    /** 邮箱类字段（保留首字符与域名） */
    private static final Set<String> EMAIL_KEYS = Set.of("email", "mail");

    /** 密码整体掩码值 */
    private static final String FULL_MASK = "******";
    /** 令牌整体掩码值 */
    private static final String TOKEN_MASK = "***";
    /** 手机号保留前 3 位 */
    private static final int MOBILE_PREFIX_LENGTH = 3;
    /** 手机号保留后 4 位 */
    private static final int MOBILE_SUFFIX_LENGTH = 4;
    /** 身份证保留前 6 位 */
    private static final int ID_CARD_PREFIX_LENGTH = 6;
    /** 身份证保留后 4 位 */
    private static final int ID_CARD_SUFFIX_LENGTH = 4;
    /** 银行卡保留后 4 位 */
    private static final int BANK_CARD_SUFFIX_LENGTH = 4;
    /** 银行卡掩码前缀 */
    private static final String BANK_CARD_MASK_PREFIX = "**** **** **** ";
    /** 邮箱掩码中段 */
    private static final String EMAIL_MASK_MIDDLE = "***";
    /** 递归深度上限 */
    private static final int MAX_DEPTH = 8;
    /** 参数 JSON 长度上限（字符） */
    private static final int MAX_PARAM_LENGTH = 4096;
    /** 不参与递归展开的基础设施包前缀 */
    private static final List<String> OPAQUE_PACKAGE_PREFIXES =
            List.of("java.", "javax.", "jakarta.", "org.springframework.", "org.apache.", "io.netty.");
    /** 掩码占位符分隔符 */
    private static final String MASK_CHAR = "*";

    private final ObjectMapper objectMapper;

    /**
     * 附加敏感键名（AUTH-11 配置化）：与内置规则并集，命中整体掩码
     */
    private final Set<String> extraSecretKeys;

    /**
     * 构造脱敏器（使用内置规则）
     *
     * @param objectMapper Jackson 序列化器，用于输出脱敏后的 JSON
     */
    public SensitiveDataMasker(ObjectMapper objectMapper) {
        this(objectMapper, List.of());
    }

    /**
     * 构造脱敏器（合并附加敏感键名，AUTH-11）
     *
     * @param objectMapper Jackson 序列化器，用于输出脱敏后的 JSON
     * @param extraSecretKeys 附加敏感键名（忽略大小写、去 {@code _}/{@code -} 后匹配），可为 {@code null}
     */
    public SensitiveDataMasker(ObjectMapper objectMapper, Collection<String> extraSecretKeys) {
        this.objectMapper = objectMapper;
        Set<String> normalized = new LinkedHashSet<>();
        if (extraSecretKeys != null) {
            for (String key : extraSecretKeys) {
                if (key != null && !key.isBlank()) {
                    normalized.add(normalizeKey(key));
                }
            }
        }
        this.extraSecretKeys = normalized;
    }

    /**
     * 脱敏并序列化方法参数，用于写入 {@code request_params}
     *
     * @param args 方法参数数组，可为 {@code null} 或空
     * @return 脱敏后的 JSON 文本；无参数或序列化失败时返回 {@code null}
     */
    public String maskArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Object payload = args.length == 1 ? args[0] : List.of(args);
        if (payload == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(mask(payload));
            if (json.length() <= MAX_PARAM_LENGTH) {
                return json;
            }
            log.debug("审计参数超长已截断: length={}", json.length());
            return json.substring(0, MAX_PARAM_LENGTH);
        } catch (Exception e) {
            // 参数脱敏失败不阻断审计主流程，仅放弃参数记录
            log.warn("审计参数脱敏序列化失败，本次不记录参数", e);
            return null;
        }
    }

    /**
     * 脱敏 JSON 文本（请求体 / 响应体通用，REQ-SEC-006）。
     *
     * <p>解析为 Map/List 后递归掩码再序列化；解析失败（非合法 JSON）时**仅按
     * {@value #MAX_PARAM_LENGTH} 截断**，不抛异常、不丢内容。</p>
     *
     * <p>等价于 {@code maskJson(null, json)}，不读取字段级 {@code @Sensitive} 注解。</p>
     *
     * @param json 待脱敏的 JSON 文本，可为 {@code null} 或空
     * @return 脱敏并截断后的 JSON 文本；入参为空时返回 {@code null}
     */
    public String maskJson(String json) {
        return maskJson(null, json);
    }

    /**
     * 脱敏 JSON 文本（返回体专用）：在 {@link #maskJson(String)} 基础上，额外按
     * {@code target} 上标注 {@link Sensitive} 的字段名整体掩码（字段级注解兜底，AUTH-11）。
     *
     * <p>{@code target} 提供被记录类型字段的注解来源；其对象图内任意层级标注
     * {@code @Sensitive} 的字段名，都会在解析出的 JSON 中按名整体掩码，<b>不依赖</b>
     * key 规则是否命中。旧签名 {@link #maskJson(String)} 行为保持不变。</p>
     *
     * @param target 被记录对象（{@code @Sensitive} 字段注解来源），可为 {@code null}
     * @param json 待脱敏的 JSON 文本，可为 {@code null} 或空
     * @return 脱敏并截断后的 JSON 文本；{@code json} 为空时返回 {@code null}
     */
    public String maskJson(Object target, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Set<String> forcedMaskKeys = new LinkedHashSet<>();
        collectSensitiveNames(target, 0, forcedMaskKeys);
        String masked = json;
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            masked = objectMapper.writeValueAsString(mask(parsed, 0, forcedMaskKeys));
        } catch (Exception e) {
            // 非合法 JSON：放弃结构化脱敏，仅截断保留原文，避免丢日志
            log.debug("日志体非合法 JSON，仅截断不脱敏: length={}", json.length());
        }
        return masked.length() <= MAX_PARAM_LENGTH ? masked : masked.substring(0, MAX_PARAM_LENGTH);
    }

    /**
     * 递归掩码任意对象
     *
     * @param value 待掩码对象
     * @return 掩码后的 Map / List / 标量结构
     */
    public Object mask(Object value) {
        return mask(value, 0, Set.of());
    }

    /**
     * 递归掩码任意对象，带深度控制与强制整体掩码字段名集合
     *
     * @param value 待掩码对象
     * @param depth 当前递归深度
     * @param forcedMaskKeys 需整体掩码的字段名（归一化后），如 {@code @Sensitive} 字段
     * @return 掩码后的 Map / List / 标量结构
     */
    private Object mask(Object value, int depth, Set<String> forcedMaskKeys) {
        if (value == null) {
            return null;
        }
        if (depth > MAX_DEPTH) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> masked = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                masked.put(key, maskValueByKey(key, entry.getValue(), depth + 1, forcedMaskKeys));
            }
            return masked;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> masked = new ArrayList<>(collection.size());
            for (Object element : collection) {
                masked.add(mask(element, depth + 1, forcedMaskKeys));
            }
            return masked;
        }
        if (value.getClass().isArray()) {
            return maskArray(value, depth, forcedMaskKeys);
        }
        if (isScalar(value)) {
            return value;
        }
        if (isOpaque(value)) {
            // 基础设施类只留类型名，避免遍历 Servlet 请求等对象导致结构失控
            return "[" + value.getClass().getSimpleName() + "]";
        }
        return maskBean(value, depth, forcedMaskKeys);
    }

    /**
     * 掩码数组；基本类型数组仅保留类型名，不逐元素展开
     *
     * @param array 待掩码数组
     * @param depth 当前递归深度
     * @param forcedMaskKeys 需整体掩码的字段名（归一化后）
     * @return 掩码后的 List 或类型名占位串
     */
    private Object maskArray(Object array, int depth, Set<String> forcedMaskKeys) {
        if (!array.getClass().getComponentType().isPrimitive()) {
            Object[] elements = (Object[]) array;
            List<Object> masked = new ArrayList<>(elements.length);
            for (Object element : elements) {
                masked.add(mask(element, depth + 1, forcedMaskKeys));
            }
            return masked;
        }
        // 基本类型数组（如 byte[]）不逐元素展开，避免体积膨胀
        return "[" + array.getClass().getSimpleName() + "]";
    }

    /**
     * 反射遍历 JavaBean 字段并按字段名规则掩码
     *
     * @param bean 待掩码对象
     * @param depth 当前递归深度
     * @param forcedMaskKeys 需整体掩码的字段名（归一化后）
     * @return 字段名到掩码值的 Map
     */
    private Object maskBean(Object bean, int depth, Set<String> forcedMaskKeys) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Class<?> current = bean.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (!field.trySetAccessible()) {
                    continue;
                }
                try {
                    masked.put(field.getName(),
                            maskValueByKey(field.getName(), field.get(bean), depth + 1, forcedMaskKeys));
                } catch (IllegalAccessException e) {
                    log.debug("审计参数反射读取字段失败，跳过: field={}", field.getName(), e);
                }
            }
        }
        return masked;
    }

    /**
     * 按字段名命中规则表取值，未命中则继续递归
     *
     * @param key 字段名
     * @param value 字段值
     * @param depth 当前递归深度
     * @param forcedMaskKeys 需整体掩码的字段名（归一化后）
     * @return 掩码后的字段值
     */
    private Object maskValueByKey(String key, Object value, int depth, Set<String> forcedMaskKeys) {
        if (value == null) {
            return null;
        }
        String normalized = normalizeKey(key);
        if (SECRET_KEYS.contains(normalized) || extraSecretKeys.contains(normalized)
                || forcedMaskKeys.contains(normalized)) {
            return FULL_MASK;
        }
        if (TOKEN_KEYS.contains(normalized)) {
            return TOKEN_MASK;
        }
        String text = value instanceof String str ? str : String.valueOf(value);
        if (MOBILE_KEYS.contains(normalized)) {
            return maskMobile(text);
        }
        if (ID_CARD_KEYS.contains(normalized)) {
            return maskIdCard(text);
        }
        if (BANK_CARD_KEYS.contains(normalized)) {
            return maskBankCard(text);
        }
        if (EMAIL_KEYS.contains(normalized)) {
            return maskEmail(text);
        }
        return mask(value, depth, forcedMaskKeys);
    }

    /**
     * 递归遍历被记录对象图，收集全部标注 {@link Sensitive} 的字段名（归一化）
     *
     * @param value 待遍历对象
     * @param depth 当前递归深度
     * @param names 收集到的敏感字段名（归一化后）输出集合
     */
    private void collectSensitiveNames(Object value, int depth, Set<String> names) {
        if (value == null || depth > MAX_DEPTH) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object element : map.values()) {
                collectSensitiveNames(element, depth + 1, names);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                collectSensitiveNames(element, depth + 1, names);
            }
            return;
        }
        if (value.getClass().isArray()) {
            if (!value.getClass().getComponentType().isPrimitive()) {
                for (Object element : (Object[]) value) {
                    collectSensitiveNames(element, depth + 1, names);
                }
            }
            return;
        }
        if (isScalar(value) || isOpaque(value)) {
            return;
        }
        for (Class<?> current = value.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                collectSensitiveName(field, value, depth, names);
            }
        }
    }

    /**
     * 处理单个字段：标注 {@link Sensitive} 则登记其字段名，并递归收集其值内的注解
     *
     * @param field 待处理字段
     * @param bean 字段所属对象
     * @param depth 当前递归深度
     * @param names 收集到的敏感字段名（归一化后）输出集合
     */
    private void collectSensitiveName(Field field, Object bean, int depth, Set<String> names) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
            return;
        }
        if (field.isAnnotationPresent(Sensitive.class)) {
            names.add(normalizeKey(field.getName()));
        }
        if (!field.trySetAccessible()) {
            return;
        }
        try {
            collectSensitiveNames(field.get(bean), depth + 1, names);
        } catch (IllegalAccessException e) {
            log.debug("审计字段注解收集读取失败，跳过: field={}", field.getName(), e);
        }
    }

    /** 手机号：保留前 3 后 4，中间固定 4 个掩码符；长度不足时整体掩码 */
    private static String maskMobile(String value) {
        if (value.length() < MOBILE_PREFIX_LENGTH + MOBILE_SUFFIX_LENGTH + 1) {
            return FULL_MASK;
        }
        return value.substring(0, MOBILE_PREFIX_LENGTH)
                + MASK_CHAR.repeat(4)
                + value.substring(value.length() - MOBILE_SUFFIX_LENGTH);
    }

    /** 身份证：保留前 6 后 4，中间掩码；长度不足时整体掩码 */
    private static String maskIdCard(String value) {
        if (value.length() < ID_CARD_PREFIX_LENGTH + ID_CARD_SUFFIX_LENGTH + 1) {
            return FULL_MASK;
        }
        int middleLength = value.length() - ID_CARD_PREFIX_LENGTH - ID_CARD_SUFFIX_LENGTH;
        return value.substring(0, ID_CARD_PREFIX_LENGTH)
                + MASK_CHAR.repeat(middleLength)
                + value.substring(value.length() - ID_CARD_SUFFIX_LENGTH);
    }

    /** 银行卡：仅保留后 4 位；长度不足时整体掩码 */
    private static String maskBankCard(String value) {
        if (value.length() < BANK_CARD_SUFFIX_LENGTH) {
            return FULL_MASK;
        }
        return BANK_CARD_MASK_PREFIX + value.substring(value.length() - BANK_CARD_SUFFIX_LENGTH);
    }

    /** 邮箱：保留首字符与域名（名称部分掩码）；不含 {@code @} 时整体掩码 */
    private static String maskEmail(String value) {
        int atIndex = value.indexOf('@');
        if (atIndex <= 0) {
            return FULL_MASK;
        }
        return value.charAt(0) + EMAIL_MASK_MIDDLE + value.substring(atIndex);
    }

    /** 字段名归一化：小写并去掉 {@code _} / {@code -} */
    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    /** 是否可直接落库的标量类型 */
    private static boolean isScalar(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof Temporal
                || value instanceof Date
                || value instanceof UUID
                || value instanceof Locale;
    }

    /** 是否为不做递归展开的基础设施类型 */
    private static boolean isOpaque(Object value) {
        String className = value.getClass().getName();
        return OPAQUE_PACKAGE_PREFIXES.stream().anyMatch(className::startsWith);
    }
}
