package com.bluewhale.sts.auth.common.codec;

import com.bluewhale.sts.auth.common.AuthProperties;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 编解码器 —— 只做一件事：用 HMAC-SHA512 签发并校验令牌。
 *
 * <p><b>为什么自己写而不用第三方库：</b>
 * <ul>
 *   <li>本仓的依赖取舍是"最小依赖"（{@code docs/decisions/0002} / {@code 0003}）。
 *       单服务、对称密钥的场景只需要 HMAC 签名这一种能力，引入完整 JWT 库
 *       （jjwt / nimbus）带来的是一整套用不到的算法与序列化适配；</li>
 *   <li>jjwt 的 JSON 适配默认绑 Jackson 2，而本仓运行在 **Jackson 3**（Boot 4）下，
 *       两代 Jackson 共存正是本项目已记录的一类隐雷；</li>
 *   <li>HS512 的"自己写"其实只是 <b>HMAC-SHA512 + Base64URL + JSON 拼接</b>三段，
 *       密码学原语全部来自 JDK，没有自研算法。</li>
 * </ul>
 *
 * <p><b>安全上刻意做的三件事</b>（少任何一条都会留下已知漏洞）：
 * <ol>
 *   <li><b>算法锁定</b>：只接受 header 里 {@code alg = HS512} 的令牌。
 *       不锁算法是 JWT 最经典的漏洞（alg=none / alg 混淆）；
 *       这里先校验 alg，再用本服务的密钥重算签名。</li>
 *   <li><b>恒定时间比较</b>：签名与口令摘要都用 {@link MessageDigest#isEqual} 比较，
 *       而不是 {@code Arrays.equals} —— 后者会因"第几位不同"提前返回，
 *       把比较耗时变成可被利用的旁路。</li>
 *   <li><b>iss / aud / exp 三项全查</b>：只验签名不验声明，等于接受"别的系统签发的、
 *       或早已过期的"令牌。</li>
 * </ol>
 *
 * <p><b>不做的：</b>不实现 RS256（非对称）。单服务对称密钥足够，且参照实现里
 * 那个 RS256 适配器三个方法都是空占位（{@code Rsa384JwtTokenAdapter}），
 * 与其照抄一个不可用的实现，不如明确只支持 HS512，需要多方验签时再引入。
 */
public class JwtCodec {

    private static final String ALG = "HS512";
    private static final String MAC_ALGORITHM = "HmacSHA512";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final String issuer;
    private final String audience;
    private final ObjectMapper objectMapper;

    public JwtCodec(AuthProperties properties, ObjectMapper objectMapper) {
        String rawSecret = properties.getJwt().getSecret();
        if (rawSecret == null || rawSecret.isBlank()) {
            // 快速失败：把"密钥没配"这件事在启动时暴露，而不是等到第一次登录
            throw new IllegalStateException(
                    "bluewhale.auth.jwt.secret 未配置：请通过环境变量 BW_JWT_SECRET 注入非空密钥");
        }
        this.secret = rawSecret.getBytes(StandardCharsets.UTF_8);
        this.issuer = properties.getJwt().getIssuer();
        this.audience = properties.getJwt().getAudience();
        this.objectMapper = objectMapper;
    }

    /**
     * 签发令牌。
     *
     * @param userId    用户主键
     * @param tenantId  租户主键
     * @param username  登录名（冗余进令牌，让日志与前端展示不必回查库）
     * @param tokenType 令牌类型：{@code access} / {@code refresh}
     * @param jti       令牌唯一标识，刷新令牌靠它做撤销与重放检测
     * @param nowMillis 签发时刻（毫秒），由调用方传入以便测试可控
     * @param ttl       有效期
     */
    public String issue(long userId, long tenantId, String username, String tokenType,
                        String jti, long nowMillis, java.time.Duration ttl) {

        long expiresAt = nowMillis + ttl.toMillis();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", ALG);
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(userId));
        payload.put("uid", userId);
        payload.put("tid", tenantId);
        payload.put("uname", username);
        payload.put("typ", tokenType);
        payload.put("jti", jti);
        payload.put("iss", issuer);
        payload.put("aud", audience);
        payload.put("iat", nowMillis / 1000L);
        payload.put("exp", expiresAt / 1000L);

        String signingInput = encode(header) + "." + encode(payload);
        return signingInput + "." + encodeBytes(sign(signingInput));
    }

    /**
     * 校验并解析令牌。
     *
     * <p>任何一项不满足都抛 {@link JwtVerificationException}，由调用方翻译成 401 类响应。
     * 用异常而不是 {@code null} 返回值：调用方无法"忘记判空"，也就不会出现
     * "校验失败却继续往下走"这种最危险的失误。
     *
     * @param token 原始令牌（不含 {@code Bearer } 前缀）
     */
    public JwtPayload verify(String token, long nowMillis) {
        if (token == null || token.isBlank()) {
            throw new JwtVerificationException("令牌为空");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtVerificationException("令牌格式不正确");
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = sign(signingInput);

        byte[] provided;
        try {
            provided = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            throw new JwtVerificationException("签名不是合法的 Base64URL");
        }
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new JwtVerificationException("签名校验失败");
        }

        Map<String, Object> header = decodeToMap(parts[0], "头部");
        Object alg = header.get("alg");
        if (!ALG.equals(alg)) {
            throw new JwtVerificationException("不接受的签名算法：" + alg);
        }

        Map<String, Object> payload = decodeToMap(parts[1], "载荷");
        if (!issuer.equals(payload.get("iss")) || !audience.equals(payload.get("aud"))) {
            throw new JwtVerificationException("签发方或受众不匹配");
        }

        long expSeconds = asLong(payload.get("exp"), "exp");
        if (expSeconds * 1000L <= nowMillis) {
            throw new JwtExpiredException("令牌已过期");
        }

        return new JwtPayload(
                asLong(payload.get("uid"), "uid"),
                asLong(payload.get("tid"), "tid"),
                asString(payload.get("uname")),
                asString(payload.get("typ")),
                asString(payload.get("jti")),
                asLong(payload.get("iat"), "iat") * 1000L,
                expSeconds * 1000L);
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, MAC_ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            // HmacSHA512 是 JDK 必备算法，走到这里说明运行环境异常，不应静默
            throw new IllegalStateException("JWT 签名失败", ex);
        }
    }

    private String encode(Map<String, Object> value) {
        return ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    /** 签名字节段的 Base64URL 编码（不带填充） */
    private static String encodeBytes(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeToMap(String part, String what) {
        try {
            return objectMapper.readValue(DECODER.decode(part), Map.class);
        } catch (Exception ex) {
            throw new JwtVerificationException("令牌" + what + "不是合法 JSON");
        }
    }

    private static long asLong(Object value, String name) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                // 落到下面的统一报错
            }
        }
        throw new JwtVerificationException("令牌声明 " + name + " 不是数字");
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}
