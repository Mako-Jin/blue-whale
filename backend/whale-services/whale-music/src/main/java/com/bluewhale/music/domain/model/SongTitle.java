package com.bluewhale.music.domain.model;

import java.util.Objects;

/**
 * 歌曲标题值对象。
 *
 * <p>标题去首尾空白后长度必须在 1–120 之间；空标题或超长标题属于非法领域输入，
 * 在创建聚合前即被拒绝。</p>
 *
 * @author BlueWhale
 */
public final class SongTitle {

    /** 标题最大长度 */
    private static final int MAX_LENGTH = 120;

    /** 规范化后的标题文本 */
    private final String value;

    /**
     * 私有构造，仅通过 {@link #of(String)} 创建。
     *
     * @param value 已规范化的标题
     */
    private SongTitle(String value) {
        this.value = value;
    }

    /**
     * 由原始输入创建歌曲标题值对象。
     *
     * @param raw 原始标题
     * @return 歌曲标题值对象
     * @throws IllegalArgumentException 标题为空或超过 120 字符
     */
    public static SongTitle of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("歌曲标题不能为空");
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("歌曲标题不能为空");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("歌曲标题长度不能超过 " + MAX_LENGTH);
        }
        return new SongTitle(normalized);
    }

    /**
     * 获取规范化后的标题文本。
     *
     * @return 标题文本
     */
    public String value() {
        return value;
    }

    /**
     * 判断两个歌曲标题是否相同。
     *
     * @param other 比较对象
     * @return 标题文本一致时返回 {@code true}
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SongTitle songTitle)) {
            return false;
        }
        return value.equals(songTitle.value);
    }

    /**
     * 计算哈希值。
     *
     * @return 基于标题文本的哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /**
     * 返回标题文本。
     *
     * @return 标题文本
     */
    @Override
    public String toString() {
        return value;
    }
}
