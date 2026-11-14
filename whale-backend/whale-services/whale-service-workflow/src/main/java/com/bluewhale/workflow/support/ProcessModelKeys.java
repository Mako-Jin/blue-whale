package com.bluewhale.workflow.support;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 流程模型键规则：节点 key、流转 key、分组 key 共用同一字符集与长度。
 *
 * @author BlueWhale
 */
public final class ProcessModelKeys {

    /** 键最大长度 */
    public static final int MAX_KEY_LENGTH = 64;

    /** 键合法字符集：字母/数字/下划线/连字符，1~64 字符 */
    public static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1," + MAX_KEY_LENGTH + "}$");

    /** 工具类不允许实例化 */
    private ProcessModelKeys() {
    }

    /**
     * 生成不与已有键冲突的合成键。
     *
     * @param base 基础键
     * @param used 已占用键集合
     * @return 唯一键
     */
    public static String uniqueKey(String base, Set<String> used) {
        if (!used.contains(base)) {
            return base;
        }
        int suffix = 2;
        String candidate = base + "_" + suffix;
        while (used.contains(candidate)) {
            suffix++;
            candidate = base + "_" + suffix;
        }
        return candidate;
    }
}
