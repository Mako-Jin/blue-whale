package com.bluewhale.gateway.security;

import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.Map;

/**
 * JWT 头解析：在验签前读取 {@code alg} 与 {@code kid}，用于选择验签密钥。
 *
 * <p>jjwt 的 {@code parseSignedClaims} 必须先有 key，无法先读头再异步取 key；这里只对首段
 * Base64URL 做解码，不信任其中任何内容，只用于「选哪把公钥」，最终仍以验签结果为准。</p>
 *
 * @param keyId     密钥标识，可能为空
 * @param algorithm 签名算法，可能为空
 * @author BlueWhale
 */
public record TokenHeader(String keyId, String algorithm) {

    /** JWT 头字段名：算法 */
    private static final String FIELD_ALGORITHM = "alg";

    /** JWT 头字段名：密钥标识 */
    private static final String FIELD_KEY_ID = "kid";

    /** JWT 段分隔符 */
    private static final String SEGMENT_SEPARATOR = "\\.";

    /** JWT 最少段数：header.payload（签名段在解析签名令牌时必填，这里只做头解析） */
    private static final int MIN_SEGMENTS = 2;

    /** 仅用于解析令牌头，不参与任何业务序列化 */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * 解析 JWT 头（不做签名校验）
     *
     * @param token JWT 原文
     * @return 头信息
     * @throws IllegalArgumentException 令牌结构非法或头不是合法 JSON 对象
     */
    public static TokenHeader parse(String token) {
        String[] segments = token.split(SEGMENT_SEPARATOR);
        if (segments.length < MIN_SEGMENTS) {
            throw new IllegalArgumentException("JWT 结构非法：段数不足");
        }
        Map<?, ?> header;
        try {
            header = MAPPER.readValue(Base64.getUrlDecoder().decode(segments[0]), Map.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JWT 头不是合法的 Base64URL JSON", e);
        }
        return new TokenHeader(stringify(header.get(FIELD_KEY_ID)), stringify(header.get(FIELD_ALGORITHM)));
    }

    /**
     * 空安全字符串化
     *
     * @param value 原始值
     * @return 字符串；缺失返回空串
     */
    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
