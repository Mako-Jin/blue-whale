package com.bluewhale.agent.support.embedding;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 本地确定性哈希向量（feature hashing）：无外部依赖、无出网、跨进程稳定。
 *
 * <p>把文本拆成三类特征（英文/数字词、中文单字、中文二元组），用 FNV-1a 哈希映射到
 * 固定维度的桶并带符号累加，最后做 L2 归一化。向量间余弦相似度近似词面重合度，
 * 作为无远程 embedding 服务时的降级通道，保证混合检索链路可运行。</p>
 *
 * @author BlueWhale
 */
public final class HashEmbeddingProvider implements EmbeddingProvider {

    /** 默认向量维度 */
    public static final int DEFAULT_DIMENSION = 256;

    /** 单文本参与特征提取的最大字符数（避免超长文档拖慢构建） */
    private static final int MAX_TEXT_CHARS = 4000;

    /** FNV-1a 64 位初始值 */
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;

    /** FNV-1a 64 位质数 */
    private static final long FNV_PRIME = 0x100000001b3L;

    /** 符号位掩码 */
    private static final long SIGN_MASK = 0x4000000000000000L;

    /** 向量维度 */
    private final int dimension;

    /**
     * 构造哈希向量提供方。
     *
     * @param dimension 期望维度（小于等于 0 时回落 {@link #DEFAULT_DIMENSION}）
     */
    public HashEmbeddingProvider(int dimension) {
        this.dimension = dimension > 0 ? dimension : DEFAULT_DIMENSION;
    }

    @Override
    public String name() {
        return "hash";
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        float[] vector = new float[dimension];
        for (String feature : features(text)) {
            long hash = fnv1a(feature);
            int bucket = (int) Math.floorMod(hash ^ (hash >>> 32), dimension);
            vector[bucket] += (hash & SIGN_MASK) == 0 ? 1.0F : -1.0F;
        }
        return EmbeddingProvider.normalize(vector);
    }

    /**
     * 提取文本特征（英文词、中文单字、中文二元组）。
     *
     * @param text 原始文本
     * @return 特征列表（保留重复，重复次数即权重）
     */
    static List<String> features(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.length() > MAX_TEXT_CHARS) {
            lower = lower.substring(0, MAX_TEXT_CHARS);
        }
        List<String> features = new ArrayList<>();
        StringBuilder ascii = new StringBuilder();
        StringBuilder han = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (isHan(c)) {
                flushAscii(ascii, features);
                han.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                flushHan(han, features);
                ascii.append(c);
            } else {
                flushAscii(ascii, features);
                flushHan(han, features);
            }
        }
        flushAscii(ascii, features);
        flushHan(han, features);
        return features;
    }

    /**
     * 输出并清空英文/数字词特征。
     *
     * @param buffer   缓冲
     * @param features 特征容器
     */
    private static void flushAscii(StringBuilder buffer, List<String> features) {
        if (buffer.length() == 0) {
            return;
        }
        features.add("w:" + buffer);
        buffer.setLength(0);
    }

    /**
     * 输出并清空中文单字与二元组特征。
     *
     * @param buffer   缓冲
     * @param features 特征容器
     */
    private static void flushHan(StringBuilder buffer, List<String> features) {
        if (buffer.length() == 0) {
            return;
        }
        for (int i = 0; i < buffer.length(); i++) {
            features.add("c:" + buffer.charAt(i));
            if (i + 1 < buffer.length()) {
                features.add("b:" + buffer.charAt(i) + String.valueOf(buffer.charAt(i + 1)));
            }
        }
        buffer.setLength(0);
    }

    /**
     * 判断是否汉字。
     *
     * @param c 字符
     * @return 汉字返回 {@code true}
     */
    private static boolean isHan(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }

    /**
     * FNV-1a 64 位哈希。
     *
     * @param feature 特征串
     * @return 哈希值
     */
    static long fnv1a(String feature) {
        long hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < feature.length(); i++) {
            hash ^= feature.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
