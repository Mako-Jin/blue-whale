package com.bluewhale.audit.support;

import com.bluewhale.common.audit.entity.AuditLogRecord;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

/**
 * 审计归档完整性摘要与校验清单（等保 2.0 8.1.4.3 b) c)）。
 *
 * <p>解决两个问题：</p>
 * <ol>
 *   <li><b>单个归档文件是否被改过</b>：每批 CSV 计算 SHA-256，写进 {@code audit_archive_watermark}
 *       与本批 {@code .sha256} 清单，任何人重算即可比对；</li>
 *   <li><b>整条归档序列是否被插入/删除过批次</b>：链式哈希
 *       {@code chain(n) = sha256(chain(n-1) || sha256(batch n))}，首批以空串作为前驱。
 *       只改一批会让后续所有链值对不上；</li>
 *   <li><b>有库写权限的人能否无痕伪造</b>：配置 {@code whale.audit.export.sign-key} 后，
 *       清单尾部追加 HMAC-SHA256 签名。写者没有密钥就无法为新批次生成可验证签名，
 *       链虽可自洽但签名过不了——这是「哈希」升级为「签名」的一步。</li>
 * </ol>
 *
 * <p>无状态工具类。签名密钥为空时 {@code hmac=} 行留空，清单仍可读，只是缺少密钥级凭证；
 * 生产建议显式配置并定期轮换（轮换会中断旧批次的签名可比对性，故需保留历史密钥备查）。</p>
 *
 * @author BlueWhale
 */
public final class AuditArchiveDigest {

    /** 哈希算法（SHA-256 足够，配合链式与签名已能覆盖归档篡改场景） */
    private static final String HASH_ALGORITHM = "SHA-256";

    /** 签名算法 */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** 清单格式版本：解析方据此判断字段集合，格式变更必须升版本 */
    private static final String MANIFEST_FORMAT = "whale-audit-archive/1";

    /** 清单字段分隔符 */
    private static final String KV_SEPARATOR = "=";

    /** 清单行分隔符（与 CSV 一致统一 LF，避免跨平台差异导致哈希不一致） */
    private static final String LINE_SEPARATOR = "\n";

    /** 清单签名行前缀：签名覆盖此前全部字节，解析时据此切分 */
    private static final String HMAC_PREFIX = "hmac=";

    /** 清单中的时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** CSV 后缀（清单文件名由 CSV 文件名派生） */
    private static final String CSV_SUFFIX = ".csv";

    /** 清单后缀（约定俗成的 sha256 校验文件后缀，sha256sum 工具可直接识别） */
    private static final String MANIFEST_SUFFIX = ".sha256";

    /**
     * 私有构造：工具类禁止实例化
     */
    private AuditArchiveDigest() {
    }

    /**
     * 计算字节内容的 SHA-256（十六进制小写）
     *
     * @param content 待计算内容
     * @return 64 位十六进制小写摘要
     */
    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，缺失说明运行环境异常，直接抛出由上层受控失败
            throw new IllegalStateException("JDK 缺少 SHA-256 实现，归档完整性无法保证", e);
        }
    }

    /**
     * 计算链式哈希：{@code sha256(前驱链值 || 本批摘要)}
     *
     * @param previousChain 上一批的 {@code chain_sha256}；首批传 {@code null} 或空串
     * @param batchSha256   本批 CSV 的 SHA-256
     * @return 本批链值（64 位十六进制小写）
     */
    public static String chainSha256(String previousChain, String batchSha256) {
        String previous = previousChain == null ? "" : previousChain;
        return sha256Hex((previous + batchSha256).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 HMAC-SHA256（十六进制小写）
     *
     * @param key     签名密钥；为空时返回空串（表示不做签名）
     * @param content 待签名字节
     * @return 64 位十六进制小写签名；密钥为空时返回空串
     */
    public static String hmacSha256Hex(String key, byte[] content) {
        if (key == null || key.isBlank()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(content));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败，归档清单无法签名", e);
        }
    }

    /**
     * 由 CSV 文件名派生清单文件名：{@code auditLog-archive-xxx.csv -> auditLog-archive-xxx.csv.sha256}
     *
     * <p>保留 {@code .csv} 再追加 {@code .sha256}，使 {@code sha256sum -c} 能直接按清单里的
     * 文件名定位同目录的 CSV，无需额外约定。</p>
     *
     * @param csvFileName CSV 文件名
     * @return 清单文件名
     */
    public static String manifestFileName(String csvFileName) {
        String base = csvFileName.endsWith(CSV_SUFFIX)
                ? csvFileName.substring(0, csvFileName.length() - CSV_SUFFIX.length())
                : csvFileName;
        return base + CSV_SUFFIX + MANIFEST_SUFFIX;
    }

    /**
     * 生成本批归档校验清单（{@code key=value} 逐行，LF 结尾，尾部为 HMAC 签名行）
     *
     * <p>签名覆盖清单中 {@code hmac=} 之前的<b>全部字节</b>，因此任何字段被改都会导致验签失败；
     * 校验实现见 {@code scripts/verify/archive-hash-verify.py}。</p>
     *
     * @param objectKey   CSV 在对象存储的键（可为 {@code null}，落为空串）
     * @param csvFileName CSV 文件名（清单内记录，供 {@code sha256sum -c} 使用）
     * @param batchSha256 本批 CSV 的 SHA-256
     * @param chainSha256 本批链值
     * @param records     本批归档记录（用于取行数与时间区间；不可为空）
     * @param signKey     签名密钥（为空则不签名，{@code hmac=} 行留空）
     * @return 清单字节内容（UTF-8）
     */
    public static byte[] manifestBytes(String objectKey, String csvFileName, String batchSha256, String chainSha256,
                                       List<AuditLogRecord> records, String signKey) {
        AuditLogRecord first = records.get(0);
        AuditLogRecord last = records.get(records.size() - 1);
        StringBuilder body = new StringBuilder();
        appendLine(body, "format", MANIFEST_FORMAT);
        appendLine(body, "objectKey", objectKey == null ? "" : objectKey);
        appendLine(body, "csvFile", csvFileName);
        appendLine(body, "sha256", batchSha256);
        appendLine(body, "chainSha256", chainSha256);
        appendLine(body, "rows", String.valueOf(records.size()));
        appendLine(body, "firstId", String.valueOf(first.getId()));
        appendLine(body, "lastId", String.valueOf(last.getId()));
        appendLine(body, "firstCreateTime", formatTime(first.getCreateTime()));
        appendLine(body, "lastCreateTime", formatTime(last.getCreateTime()));
        appendLine(body, "createdAt", formatTime(LocalDateTime.now()));
        String signed = body.toString();
        String hmac = hmacSha256Hex(signKey, signed.getBytes(StandardCharsets.UTF_8));
        return (signed + HMAC_PREFIX + hmac + LINE_SEPARATOR).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 追加一行 {@code key=value}
     *
     * @param builder 目标缓冲
     * @param key     字段名
     * @param value   字段值（非空）
     */
    private static void appendLine(StringBuilder builder, String key, String value) {
        builder.append(key).append(KV_SEPARATOR).append(value).append(LINE_SEPARATOR);
    }

    /**
     * 空值安全的时间格式化
     *
     * @param time 时间
     * @return 固定格式文本；{@code null} 返回空串
     */
    private static String formatTime(LocalDateTime time) {
        return time == null ? "" : TIME_FORMAT.format(time);
    }
}
