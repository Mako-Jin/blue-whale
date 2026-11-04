package com.bluewhale.pdm.part.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BOM 版本号值对象工具。
 *
 * <p>格式固定为 {@code V<major>.<minor>}；新建版本递增主版本（V1.0 → V2.0）。</p>
 *
 * @author BlueWhale
 */
public final class PdmBomVersionNo {

    /** 初始版本 */
    public static final String INITIAL = "V1.0";

    /** 版本格式 */
    private static final Pattern PATTERN = Pattern.compile("V(\\d+)\\.(\\d+)");

    /**
     * 工具类禁止实例化
     */
    private PdmBomVersionNo() {
    }

    /**
     * 校验版本号格式。
     *
     * @param versionNo 版本号
     * @return 规范化版本号
     */
    public static String requireValid(String versionNo) {
        if (versionNo == null || !PATTERN.matcher(versionNo).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "BOM 版本号必须形如 V1.0");
        }
        return versionNo;
    }

    /**
     * 计算下一个主版本。
     *
     * @param current 当前版本号
     * @return 下一个主版本
     */
    public static String nextMajor(String current) {
        Matcher matcher = PATTERN.matcher(current == null ? "" : current);
        if (!matcher.matches()) {
            return INITIAL;
        }
        long major = Long.parseLong(matcher.group(1)) + 1;
        return "V" + major + ".0";
    }

    /**
     * 计算版本排序值：major*10000 + minor。
     *
     * @param versionNo 版本号
     * @return 排序值
     */
    public static int seqOf(String versionNo) {
        Matcher matcher = PATTERN.matcher(requireValid(versionNo));
        if (!matcher.matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "BOM 版本号必须形如 V1.0");
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        return major * 10000 + minor;
    }
}
