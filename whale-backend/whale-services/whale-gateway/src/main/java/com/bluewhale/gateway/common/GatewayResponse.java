package com.bluewhale.gateway.common;

/**
 * 网关统一响应体（与业务侧 Result 风格保持一致）
 *
 * @param code    业务状态码
 * @param message 提示信息
 * @param data    业务数据载荷
 * @author BlueWhale
 */
public record GatewayResponse(int code, String message, Object data) {

    /** JSON 字符串需转义的字符表下标：{@code \} */
    private static final char BACKSLASH = '\\';

    /** JSON 字符串需转义的字符表下标：{@code "} */
    private static final char QUOTE = '"';

    /** 空格字符：所有控制字符都小于它，低于该值的字符必须转义为 {@code \\u00XX} */
    private static final char SPACE_CHAR = ' ';

    /** 十六进制字符表，用于控制字符转义 */
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * 构建仅含状态码与提示信息的响应体（数据载荷为 null）
     *
     * @param code    业务状态码
     * @param message 提示信息
     * @return 网关统一响应体
     */
    public static GatewayResponse of(int code, String message) {
        return new GatewayResponse(code, message, null);
    }

    /**
     * 手写 JSON 输出。
     *
     * <p>{@code code} 为整型、{@code data} 恒为 {@code null}，仅 {@code message} 是字符串。
     * 虽然当前所有调用点都传入服务端固定文案，但本 record 为公共类型，为防止未来传入
     * 用户可控内容造成 JSON 注入，这里对 {@code message} 做防御性转义（引号、反斜杠、控制字符）。</p>
     *
     * @return JSON 文本
     */
    public String toJson() {
        return "{\"code\":" + code + ",\"message\":\"" + escape(message) + "\",\"data\":null}";
    }

    /**
     * 对 JSON 字符串值做最小转义：{@code "}、{@code \} 与控制字符
     *
     * @param value 原始字符串，可为 {@code null}
     * @return 转义后的字符串；{@code null} 转为空串
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case QUOTE -> builder.append("\\\"");
                case BACKSLASH -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < SPACE_CHAR) {
                        builder.append("\\u00").append(HEX[(ch >> 4) & 0xF]).append(HEX[ch & 0xF]);
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }
}
