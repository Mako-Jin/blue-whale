package com.bluewhale.pdm.document.support;

import com.bluewhale.pdm.support.PdmVersionNo;

/**
 * PDM 文档版本号值对象（V{major}.{minor}）
 *
 * <p>规则：草稿从 {@code V0.1} 开始；首次发布转为 {@code V1.0}；
 * 之后每次检入/上传生成 {@code V{major}.{minor+1}}，例如 {@code V1.0 → V1.1}。
 * 版本比较只走本对象，禁止字符串直接比较；底层规则委托 {@link PdmVersionNo}。</p>
 *
 * @author BlueWhale
 */
public final class PdmDocVersionNo implements Comparable<PdmDocVersionNo> {

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
        return of(PdmVersionNo.parse(value));
    }

    /**
     * 首个草稿版本 V0.1
     *
     * @return 版本号
     */
    public static PdmDocVersionNo initialDraft() {
        return of(PdmVersionNo.initialDraft());
    }

    /**
     * 基于当前版本生成下一个草稿版本（次版本 +1）
     *
     * @return 版本号
     */
    public PdmDocVersionNo nextDraft() {
        return of(toVersionNo().nextMinor());
    }

    /**
     * 发布目标版本：草稿 V0.x 转正式 V1.0，其余保持原版本号
     *
     * @return 版本号
     */
    public PdmDocVersionNo releaseTarget() {
        return of(toVersionNo().releaseTarget());
    }

    /**
     * 版本排序值
     *
     * @return 排序值
     */
    public int seq() {
        return toVersionNo().seq();
    }

    /**
     * 版本号文本
     *
     * @return 形如 V1.0 的文本
     */
    public String value() {
        return toVersionNo().value();
    }

    /**
     * 是否草稿版本（主版本为 0）
     *
     * @return true 表示草稿
     */
    public boolean isDraft() {
        return toVersionNo().isDraft();
    }

    @Override
    public int compareTo(PdmDocVersionNo other) {
        return toVersionNo().compareTo(other.toVersionNo());
    }

    @Override
    public String toString() {
        return value();
    }

    /**
     * 转为统一版本号
     *
     * @return 统一版本号
     */
    private PdmVersionNo toVersionNo() {
        return PdmVersionNo.of(major, minor);
    }

    /**
     * 由统一版本号构造
     *
     * @param version 统一版本号
     * @return 文档版本号
     */
    private static PdmDocVersionNo of(PdmVersionNo version) {
        return new PdmDocVersionNo(version.major(), version.minor());
    }
}
