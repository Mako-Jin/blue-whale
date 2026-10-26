package com.bluewhale.contract.esign;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 签章哈希工具：SHA-256 与链式哈希。
 *
 * <p>链式哈希定义：{@code sha256(prev || contractId || versionId || signerId || signedAt || originalSha256 || signedSha256 || evidenceNo)}，
 * 任一条记录被篡改都会破坏后续链的连续性。</p>
 *
 * @author BlueWhale
 */
public final class EsignHashUtils {

    /** 链式哈希分隔符 */
    private static final String SEPARATOR = "|";

    /** 十六进制字符表 */
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * 工具类禁止实例化。
     */
    private EsignHashUtils() {
    }

    /**
     * 计算字节数组 SHA-256（小写十六进制）。
     *
     * @param content 字节内容
     * @return 64 位十六进制摘要
     */
    public static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", ex);
        }
    }

    /**
     * 计算字符串 SHA-256（小写十六进制，UTF-8）。
     *
     * @param content 字符串内容
     * @return 64 位十六进制摘要
     */
    public static String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算链式哈希。
     *
     * @param prevChain      前一条链式哈希（首条为空串）
     * @param contractId     合同ID
     * @param versionId      版本ID
     * @param signerId       签署人ID
     * @param signedAt       签署时间文本
     * @param originalSha256 盖章前文件摘要
     * @param signedSha256   签章件摘要
     * @param evidenceNo     证据编号
     * @return 链式哈希
     */
    public static String chain(String prevChain, Long contractId, Long versionId, Long signerId,
                               String signedAt, String originalSha256, String signedSha256, String evidenceNo) {
        String payload = nullToEmpty(prevChain) + SEPARATOR
                + nullToEmpty(contractId) + SEPARATOR
                + nullToEmpty(versionId) + SEPARATOR
                + nullToEmpty(signerId) + SEPARATOR
                + nullToEmpty(signedAt) + SEPARATOR
                + nullToEmpty(originalSha256) + SEPARATOR
                + nullToEmpty(signedSha256) + SEPARATOR
                + nullToEmpty(evidenceNo);
        return sha256(payload);
    }

    /**
     * 拼接链式哈希输入（供验签重算）。
     *
     * @param prevChain      前一条链式哈希
     * @param contractId     合同ID
     * @param versionId      版本ID
     * @param signerId       签署人ID
     * @param signedAt       签署时间文本
     * @param originalSha256 盖章前文件摘要
     * @param signedSha256   签章件摘要
     * @param evidenceNo     证据编号
     * @return 链式哈希输入串
     */
    public static String chainPayload(String prevChain, Long contractId, Long versionId, Long signerId,
                                      String signedAt, String originalSha256, String signedSha256, String evidenceNo) {
        return nullToEmpty(prevChain) + SEPARATOR
                + nullToEmpty(contractId) + SEPARATOR
                + nullToEmpty(versionId) + SEPARATOR
                + nullToEmpty(signerId) + SEPARATOR
                + nullToEmpty(signedAt) + SEPARATOR
                + nullToEmpty(originalSha256) + SEPARATOR
                + nullToEmpty(signedSha256) + SEPARATOR
                + nullToEmpty(evidenceNo);
    }

    /**
     * 空值转空串。
     *
     * @param value 原值
     * @return 非空字符串
     */
    private static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 字节数组转小写十六进制。
     *
     * @param bytes 字节数组
     * @return 十六进制串
     */
    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(HEX[(b >> 4) & 0x0F]).append(HEX[b & 0x0F]);
        }
        return builder.toString();
    }
}
