package com.bluewhale.agent.support.knowledge;

/**
 * 文档解析异常：区分可重试（I/O、超时）与不可重试（不支持类型、损坏文档）。
 *
 * @author BlueWhale
 */
public class FileParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 是否允许重试 */
    private final boolean retryable;

    /**
     * 构造解析异常。
     *
     * @param retryable 是否允许重试
     * @param message 失败原因
     */
    public FileParseException(boolean retryable, String message) {
        super(message);
        this.retryable = retryable;
    }

    /**
     * 构造解析异常（保留原始异常）。
     *
     * @param retryable 是否允许重试
     * @param message 失败原因
     * @param cause 原始异常
     */
    public FileParseException(boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /**
     * 是否允许重试。
     *
     * @return 可重试返回 {@code true}
     */
    public boolean isRetryable() {
        return retryable;
    }
}
