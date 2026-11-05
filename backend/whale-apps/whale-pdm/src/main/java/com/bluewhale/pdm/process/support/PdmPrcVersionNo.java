package com.bluewhale.pdm.process.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pdm.support.PdmVersionNo;

import java.util.regex.Pattern;

/**
 * 工艺路线版本号值对象工具。
 *
 * <p>格式固定为 {@code V<major>.<minor>}；新版本递增主版本（V1.0 → V2.0）。
 * 递增与排序规则委托 {@link PdmVersionNo}。</p>
 *
 * @author BlueWhale
 */
public final class PdmPrcVersionNo {

    /** 初始版本 */
    public static final String INITIAL = "V1.0";

    /** 版本格式 */
    private static final Pattern PATTERN = Pattern.compile("V(\\d+)\\.(\\d+)");

    /**
     * 工具类禁止实例化
     */
    private PdmPrcVersionNo() {
    }

    /**
     * 校验版本号格式。
     *
     * @param versionNo 版本号
     * @return 规范化版本号
     */
    public static String requireValid(String versionNo) {
        if (versionNo == null || !PATTERN.matcher(versionNo).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工艺路线版本号必须形如 V1.0");
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
        if (current == null || !PATTERN.matcher(current).matches()) {
            return INITIAL;
        }
        return PdmVersionNo.parse(current).nextMajor().value();
    }

    /**
     * 计算版本排序值：major*10000 + minor。
     *
     * @param versionNo 版本号
     * @return 排序值
     */
    public static int seqOf(String versionNo) {
        requireValid(versionNo);
        return PdmVersionNo.parse(versionNo).seq();
    }
}
