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
     * 手写 JSON 输出。message 为服务端固定文案，无转义风险
     */
    public String toJson() {
        return "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}";
    }
}
