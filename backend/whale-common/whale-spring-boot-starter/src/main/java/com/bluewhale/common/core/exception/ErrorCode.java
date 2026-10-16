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

    /** 参数错误 */
    PARAM_ERROR(400, "参数错误");

    /** 状态码 */
    private final int code;

    /** 提示消息 */
    private final String message;
}
