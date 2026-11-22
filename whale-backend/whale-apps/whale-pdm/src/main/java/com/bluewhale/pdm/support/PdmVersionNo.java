package com.bluewhale.pdm.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDM 版本号值对象（{@code V{major}.{minor}}）
 *
 * <p>统一版本规则：草稿从 {@code V0.1} 开始并逐次递增次版本；首次发布转为 {@code V1.0}；
 * 修订版本递增次版本（{@code V1.0 → V1.1}）；结构或大改递增主版本（{@code V1.x → V2.0}）。
 * 版本比较只允许走本对象，禁止字符串直接比较。</p>
 *
 * @author BlueWhale
 */
public final class PdmVersionNo implements Comparable<PdmVersionNo> {

    /** 版本号格式 */
    private static final Pattern PATTERN = Pattern.compile("^V(\\d+)\\.(\\d+)$");

    /** 首个草稿版本 */
    private static final String INITIAL_DRAFT = "V0.1";

    /** 首发版本 */
    private static final String FIRST_RELEASE = "V1.0";

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
    private PdmVersionNo(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    /**
     * 由主/次版本构造
     *
     * @param major 主版本号
     * @param minor 次版本号
     * @return 版本号值对象
     */
    public static PdmVersionNo of(int major, int minor) {
        if (major < 0 || minor < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.support.version-number-negative", "版本号不能为负数");
        }
        return new PdmVersionNo(major, minor);
    }

    /**
     * 解析版本号
     *
     * @param value 版本号文本
     * @return 版本号值对象
     */
    public static PdmVersionNo parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.support.version-empty", "版本号不能为空");
        }
        Matcher matcher = PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.support.version-format-should-v-major-version", "版本号格式应为 V{主版本}.{次版本}");
        }
        return new PdmVersionNo(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    /**
     * 首个草稿版本 {@code V0.1}
     *
     * @return 版本号
     */
    public static PdmVersionNo initialDraft() {
        return parse(INITIAL_DRAFT);
    }

    /**
     * 首发版本 {@code V1.0}
     *
     * @return 版本号
     */
    public static PdmVersionNo firstRelease() {
        return parse(FIRST_RELEASE);
    }

    /**
     * 递增次版本：草稿 {@code V0.n → V0.(n+1)}，修订 {@code V1.x → V1.(x+1)}
     *
     * @return 新版本号
     */
    public PdmVersionNo nextMinor() {
        return new PdmVersionNo(major, minor + 1);
    }

    /**
     * 递增主版本（结构/大改）：{@code V1.x → V2.0}
     *
     * @return 新版本号
     */
    public PdmVersionNo nextMajor() {
        return new PdmVersionNo(major + 1, 0);
    }

    /**
     * 发布目标版本：草稿 {@code V0.x} 转首发 {@code V1.0}，已发布版本保持不变
     *
     * @return 发布目标版本
     */
    public PdmVersionNo releaseTarget() {
        return major == 0 ? firstRelease() : this;
    }

    /**
     * 是否草稿版本（主版本为 0）
     *
     * @return true 表示草稿
     */
    public boolean isDraft() {
        return major == 0;
    }

    /**
     * 版本排序值：{@code major * 10000 + minor}
     *
     * @return 排序值
     */
    public int seq() {
        return major * 10_000 + minor;
    }

    /**
     * 主版本号
     *
     * @return 主版本号
     */
    public int major() {
        return major;
    }

    /**
     * 次版本号
     *
     * @return 次版本号
     */
    public int minor() {
        return minor;
    }

    /**
     * 版本号文本
     *
     * @return 形如 V1.0 的文本
     */
    public String value() {
        return "V" + major + "." + minor;
    }

    @Override
    public int compareTo(PdmVersionNo other) {
        return Integer.compare(seq(), other.seq());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PdmVersionNo that)) {
            return false;
        }
        return major == that.major && minor == that.minor;
    }

    @Override
    public int hashCode() {
        return 31 * major + minor;
    }

    @Override
    public String toString() {
        return value();
    }
}
