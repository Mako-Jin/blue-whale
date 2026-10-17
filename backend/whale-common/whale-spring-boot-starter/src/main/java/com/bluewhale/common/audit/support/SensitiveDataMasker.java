package com.bluewhale.common.audit.support;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
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

    public SensitiveDataMasker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
     * 递归掩码任意对象
     *
     * @param value 待掩码对象
     * @return 掩码后的 Map / List / 标量结构
     */
    public Object mask(Object value) {
        return mask(value, 0);
    }

    private Object mask(Object value, int depth) {
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
                masked.put(key, maskValueByKey(key, entry.getValue(), depth + 1));
            }
            return masked;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> masked = new ArrayList<>(collection.size());
            for (Object element : collection) {
                masked.add(mask(element, depth + 1));
            }
            return masked;
        }
        if (value.getClass().isArray()) {
            return maskArray(value, depth);
        }
        if (isScalar(value)) {
            return value;
        }
        if (isOpaque(value)) {
            // 基础设施类只留类型名，避免遍历 Servlet 请求等对象导致结构失控
            return "[" + value.getClass().getSimpleName() + "]";
        }
        return maskBean(value, depth);
    }

    private Object maskArray(Object array, int depth) {
        if (!array.getClass().getComponentType().isPrimitive()) {
            Object[] elements = (Object[]) array;
            List<Object> masked = new ArrayList<>(elements.length);
            for (Object element : elements) {
                masked.add(mask(element, depth + 1));
            }
            return masked;
        }
        // 基本类型数组（如 byte[]）不逐元素展开，避免体积膨胀
        return "[" + array.getClass().getSimpleName() + "]";
    }

    private Object maskBean(Object bean, int depth) {
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
                    masked.put(field.getName(), maskValueByKey(field.getName(), field.get(bean), depth + 1));
                } catch (IllegalAccessException e) {
                    log.debug("审计参数反射读取字段失败，跳过: field={}", field.getName(), e);
                }
            }
        }
        return masked;
    }

    /**
     * 按字段名命中规则表取值，未命中则继续递归
     */
    private Object maskValueByKey(String key, Object value, int depth) {
        if (value == null) {
            return null;
        }
        String normalized = normalizeKey(key);
        if (SECRET_KEYS.contains(normalized)) {
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
        return mask(value, depth);
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
