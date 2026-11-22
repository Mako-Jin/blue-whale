package com.bluewhale.pay.client;

/**
 * 支付客户端异常：把 HTTP 细节与 {@code Result.code} 统一收敛成业务可判断的异常。
 *
 * <p>{@link #getCode()} 与支付服务返回的 {@code Result.code} 一致；
 * {@link #getMessageKey()} 是稳定的消息键（如 {@code pay.client.http-error}），
 * 调用方可据此做国际化或分类处理。</p>
 *
 * @author BlueWhale
 */
public class PayClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码：与服务端 Result.code 或 HTTP 状态码一致 */
    private final int code;

    /** 稳定消息键 */
    private final String messageKey;

    /**
     * 构造异常
     *
     * @param code       错误码
     * @param messageKey 稳定消息键
     * @param message    可读消息
     */
    public PayClientException(int code, String messageKey, String message) {
        super(message);
        this.code = code;
        this.messageKey = messageKey;
    }

    /**
     * 构造带原因链的异常
     *
     * @param code       错误码
     * @param messageKey 稳定消息键
     * @param message    可读消息
     * @param cause      原因
     */
    public PayClientException(int code, String messageKey, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.messageKey = messageKey;
    }

    /**
     * 错误码
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }

    /**
     * 稳定消息键
     *
     * @return 消息键
     */
    public String getMessageKey() {
        return messageKey;
    }
}
