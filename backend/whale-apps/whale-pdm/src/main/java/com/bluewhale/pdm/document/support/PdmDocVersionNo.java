package com.bluewhale.pdm.document.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDM 文档版本号值对象（V{major}.{minor}）
 *
 * <p>规则：草稿从 {@code V0.1} 开始；首次发布转为 {@code V1.0}；
 * 之后每次检入/上传生成 {@code V{major}.{minor+1}}，例如 {@code V1.0 → V1.1}。
 * 版本比较只走本对象，禁止字符串直接比较。</p>
 *
 * @author BlueWhale
 */
public final class PdmDocVersionNo implements Comparable<PdmDocVersionNo> {

    /** 版本号格式 */
    private static final Pattern PATTERN = Pattern.compile("^V(\\d+)\\.(\\d+)$");

    /** 主版本号 */
    private final int major;

    /** 次版本号 */
    private final int minor;

    /**
     * 构造版本号
     *
     * @param major 主版本号
     * @param minor 次版本号
     */
    private PdmDocVersionNo(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    /**
     * 解析版本号
     *
     * @param value 版本号文本
     * @return 版本号值对象
     */
    public static PdmDocVersionNo parse(String value) {
        if (value == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "版本号不能为空");
        }
        Matcher matcher = PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "版本号格式应为 V{主版本}.{次版本}");
        }
        return new PdmDocVersionNo(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    /**
     * 首个草稿版本 V0.1
     *
     * @return 版本号
     */
    public static PdmDocVersionNo initialDraft() {
        return new PdmDocVersionNo(0, 1);
    }

    /**
     * 基于当前版本生成下一个草稿版本（次版本 +1）
     *
     * @return 版本号
     */
    public PdmDocVersionNo nextDraft() {
        return new PdmDocVersionNo(major, minor + 1);
    }

    /**
     * 发布目标版本：草稿 V0.x 转正式 V1.0，其余保持原版本号
     *
     * @return 版本号
     */
    public PdmDocVersionNo releaseTarget() {
        return major == 0 ? new PdmDocVersionNo(1, 0) : this;
    }

    /**
     * 版本排序值
     *
     * @return 排序值
     */
    public int seq() {
        return major * 10_000 + minor;
    }

    /**
     * 版本号文本
     *
     * @return 形如 V1.0 的文本
     */
    public String value() {
        return "V" + major + "." + minor;
    }

    /**
     * 是否草稿版本（主版本为 0）
     *
     * @return true 表示草稿
     */
    public boolean isDraft() {
        return major == 0;
    }

    @Override
    public int compareTo(PdmDocVersionNo other) {
        return Integer.compare(seq(), other.seq());
    }

    @Override
    public String toString() {
        return value();
    }
}
