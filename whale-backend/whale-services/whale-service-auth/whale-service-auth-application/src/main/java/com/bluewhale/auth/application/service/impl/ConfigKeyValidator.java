package com.bluewhale.auth.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import tools.jackson.databind.json.JsonMapper;

import java.util.regex.Pattern;

/**
 * 配置键与配置值校验器（auth 侧唯一守门处）。
 *
 * <p>auth 只存取、不解释业务语义，因此校验仅限「存储安全」三件事：
 * 键符合反域名点号口径（{@code notify.twin.alert}）、值是合法 JSON、
 * 值大小不超过上限。业务含义（如通知渠道取值）由消费方自行校验。</p>
 *
 * @author BlueWhale
 */
public final class ConfigKeyValidator {

    /** 配置值大小上限（字符），防止把配置表当日志表用 */
    public static final int MAX_VALUE_LENGTH = 8192;

    /**
     * 配置键口径：反域名点号前缀，至少两段，段内小写字母开头、
     * 允许小写字母/数字/中划线（如 notify.twin.alert、ui.theme）
     */
    public static final Pattern CONFIG_KEY_PATTERN =
            Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z0-9][a-z0-9-]*)+$");

    /** 前缀过滤参数口径：小写段与点号，允许空 */
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^[a-z0-9.-]*$");

    /** Jackson 3 JSON 解析器（仅用于校验值合法性，不持有状态） */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private ConfigKeyValidator() {
    }

    /**
     * 校验配置键；不合法抛 400
     *
     * @param configKey 配置键
     */
    public static void requireValidKey(String configKey) {
        if (configKey == null || !CONFIG_KEY_PATTERN.matcher(configKey).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "配置键不合法（需形如 notify.twin.alert 的反域名小写点号键）: " + configKey);
        }
        if (configKey.length() > 128) {
            throw new BizException(ErrorCode.PARAM_ERROR, "配置键超长（上限 128 字符）");
        }
    }

    /**
     * 校验前缀过滤参数；不合法抛 400（空/blank 视为不过滤，直接放行）
     *
     * @param prefix 前缀
     */
    public static void requireValidPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        if (!PREFIX_PATTERN.matcher(prefix).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "配置键前缀不合法: " + prefix);
        }
    }

    /**
     * 校验配置值：必须为合法 JSON 且不超过大小上限；不合法抛 400
     *
     * @param configValue 配置值
     */
    public static void requireValidValue(String configValue) {
        if (configValue == null || configValue.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "配置值不能为空");
        }
        if (configValue.length() > MAX_VALUE_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "配置值超长（上限 " + MAX_VALUE_LENGTH + " 字符）");
        }
        try {
            JSON_MAPPER.readTree(configValue);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "配置值必须是合法 JSON: " + e.getMessage());
        }
    }
}
