package com.bluewhale.crm.service.infrastructure.security;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmSurveyProperties;
import com.bluewhale.crm.service.domain.port.SurveyTokenPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 满意度令牌生成与校验（HMAC-SHA256，仅存哈希）。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class SurveyTokenService implements SurveyTokenPort {

    /** 随机数生成器 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 令牌分隔符 */
    private static final String SEPARATOR = ".";

    /** 默认租户 ID */
    private static final long DEFAULT_TENANT_ID = 1L;

    /** 问卷配置 */
    private final CrmSurveyProperties properties;

    /** 进程内密钥（配置留空时随机生成） */
    private String derivedSecret;

    /**
     * 生成一次性令牌。
     *
     * @param surveyNo 问卷编号
     * @return 令牌（surveyNo.nonce）
     */
    public String generate(String surveyNo) {
        return surveyNo + SEPARATOR + randomNonce();
    }

    /**
     * 计算令牌哈希。
     *
     * @param surveyNo 问卷编号
     * @param token 完整令牌
     * @return 十六进制哈希
     */
    public String hash(String surveyNo, String token) {
        String nonce = nonceOf(surveyNo, token);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((tenantId() + ":" + surveyNo + ":" + nonce)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("满意度令牌哈希计算失败", exception);
        }
    }

    /**
     * 校验令牌哈希是否匹配（常量时间比较）。
     *
     * @param surveyNo 问卷编号
     * @param token 完整令牌
     * @param expectedHash 期望哈希
     * @return 是否匹配
     */
    public boolean matches(String surveyNo, String token, String expectedHash) {
        if (expectedHash == null || token == null || !token.startsWith(surveyNo + SEPARATOR)) {
            return false;
        }
        return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
                hash(surveyNo, token).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从令牌提取问卷编号。
     *
     * @param token 完整令牌
     * @return 问卷编号
     */
    public String surveyNoOf(String token) {
        if (token == null || !token.contains(SEPARATOR)) {
            throw CrmErrorCode.exception(CrmErrorCode.SURVEY_TOKEN_INVALID);
        }
        return token.substring(0, token.lastIndexOf(SEPARATOR));
    }

    /**
     * 随机 nonce。
     *
     * @return 32 位十六进制字符串
     */
    private String randomNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 提取 nonce。
     *
     * @param surveyNo 问卷编号
     * @param token 完整令牌
     * @return nonce
     */
    private String nonceOf(String surveyNo, String token) {
        String prefix = surveyNo + SEPARATOR;
        if (token == null || !token.startsWith(prefix) || token.length() == prefix.length()) {
            throw CrmErrorCode.exception(CrmErrorCode.SURVEY_TOKEN_INVALID);
        }
        return token.substring(prefix.length());
    }

    /**
     * 当前租户 ID（纳入哈希，避免跨租户碰撞）。
     *
     * @return 租户 ID 文本
     */
    private String tenantId() {
        Long tenantId = TenantContext.get();
        return String.valueOf(tenantId == null ? DEFAULT_TENANT_ID : tenantId);
    }

    /**
     * 解析密钥；配置为空时进程内随机生成一次。
     *
     * @return 密钥
     */
    private synchronized String secret() {
        if (properties.getSecret() != null && !properties.getSecret().isBlank()) {
            return properties.getSecret();
        }
        if (derivedSecret == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            derivedSecret = HexFormat.of().formatHex(bytes);
        }
        return derivedSecret;
    }
}
