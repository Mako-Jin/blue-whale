package com.bluewhale.pm.domain.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 项目键规则：大写字母开头，2–10 位大写字母或数字，租户内唯一（唯一性由应用服务校验）。
 *
 * @author BlueWhale
 */
public final class ProjectKeyPolicy {

    /** 项目键格式：大写字母开头，长度 2–10 */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Z][A-Z0-9]{1,9}$");

    /**
     * 工具类不允许实例化
     */
    private ProjectKeyPolicy() {
    }

    /**
     * 归一化并校验项目键（去空白 + 转大写）。
     *
     * @param raw 原始项目键
     * @return 归一化后的项目键
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.domain.project-key-empty", "项目键不能为空");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.domain.project-key-start-letter-contain-2", "项目键需以字母开头，2–10 位大写字母或数字");
        }
        return normalized;
    }
}
