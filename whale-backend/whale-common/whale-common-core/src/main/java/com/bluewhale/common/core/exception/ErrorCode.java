package com.bluewhale.common.core.exception;

import lombok.Getter;

/**
 * 通用错误码枚举
 *
 * <p>{@code messageKey} 为国际化文案键，错误响应按请求 Locale 解析；
 * {@code message} 保留中文原文，作为资源包缺失时的兜底，保证展示层永不空白。</p>
 *
 * @author BlueWhale
 */
@Getter
public enum ErrorCode {

    /** 成功 */
    SUCCESS(0, "成功", "common.error.success"),

    /** 系统异常 */
    SYSTEM_ERROR(500, "系统异常", "common.error.system"),

    /** 未认证 */
    UNAUTHORIZED(401, "未认证", "common.error.unauthorized"),

    /** 无权限 */
    FORBIDDEN(403, "无权限", "common.error.forbidden"),

    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在", "common.error.not-found"),

    /** 请求方法不支持（路径存在但 HTTP 方法不匹配） */
    METHOD_NOT_ALLOWED(405, "请求方法不支持", "common.error.method-not-allowed"),

    /** 并发冲突：分布式锁获取失败（资源被占用，快速失败） */
    LOCK_ACQUIRE_FAILED(429, "系统繁忙，请稍后重试", "common.error.lock-acquire-failed"),

    /** 重复提交：同一幂等 token 的请求正在处理中或已处理完成 */
    REPEAT_SUBMIT(409, "请勿重复提交", "common.error.repeat-submit"),

    /** 参数错误 */
    PARAM_ERROR(400, "参数错误", "common.error.param-error"),

    /** 缺少必填请求参数 */
    MISSING_PARAMETER(400, "缺少参数", "common.error.missing-parameter"),

    /** 请求参数类型不匹配 */
    PARAM_TYPE_MISMATCH(400, "参数类型不匹配", "common.error.param-type-mismatch"),

    /** 请求体不可解析 */
    REQUEST_BODY_UNREADABLE(400, "请求体格式错误", "common.error.request-body-unreadable");

    /** 状态码 */
    private final int code;

    /** 提示消息（中文兜底） */
    private final String message;

    /** 国际化文案键 */
    private final String messageKey;

    /**
     * 构造错误码
     *
     * @param code       状态码
     * @param message    中文兜底提示
     * @param messageKey 国际化文案键
     */
    ErrorCode(int code, String message, String messageKey) {
        this.code = code;
        this.message = message;
        this.messageKey = messageKey;
    }
}
