package com.bluewhale.common.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用错误码枚举
 *
 * @author BlueWhale
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    /** 成功 */
    SUCCESS(0, "成功"),

    /** 系统异常 */
    SYSTEM_ERROR(500, "系统异常"),

    /** 未认证 */
    UNAUTHORIZED(401, "未认证"),

    /** 无权限 */
    FORBIDDEN(403, "无权限"),

    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),

    /** 请求方法不支持（路径存在但 HTTP 方法不匹配） */
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    /** 并发冲突：分布式锁获取失败（资源被占用，快速失败） */
    LOCK_ACQUIRE_FAILED(429, "系统繁忙，请稍后重试"),

    /** 重复提交：同一幂等 token 的请求正在处理中或已处理完成 */
    REPEAT_SUBMIT(409, "请勿重复提交"),

    /** 参数错误 */
    PARAM_ERROR(400, "参数错误");

    /** 状态码 */
    private final int code;

    /** 提示消息 */
    private final String message;
}
