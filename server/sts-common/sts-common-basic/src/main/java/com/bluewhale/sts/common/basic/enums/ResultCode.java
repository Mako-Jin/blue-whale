package com.bluewhale.sts.common.basic.enums;

import lombok.Getter;

/**
 * 统一错误码。
 *
 * <p>编码规则：<b>5 位数字 = HTTP 状态码(3 位) + 业务细分(2 位)</b>。
 * 例如 {@code 40400} 表示"HTTP 404 类别下的第 00 号细分"。
 *
 * <p>这样设计的好处：日志里看到一个码就能立刻判断是客户端问题还是服务端问题，
 * 不必回查文档；同时也给每个类别留出 100 个细分位。
 *
 * <p>{@link #SUCCESS} 恒为 {@code 0}，前端判断成功只看 {@code code === 0}。
 */
@Getter
public enum ResultCode {

    /** 成功 —— 唯一表示"业务上成功"的码 */
    SUCCESS(0, "成功"),

    /** 请求参数不合法 */
    BAD_REQUEST(40000, "请求参数错误"),

    /** 未携带凭证或凭证已失效 */
    UNAUTHORIZED(40100, "未认证或登录已过期"),

    /** 已认证但无该操作权限 */
    FORBIDDEN(40300, "没有访问权限"),

    /** 目标资源不存在 */
    NOT_FOUND(40400, "资源不存在"),

    /** 请求方法不被该接口支持 */
    METHOD_NOT_ALLOWED(40500, "请求方法不被支持"),

    /** 资源状态冲突（重复创建、并发修改等） */
    CONFLICT(40900, "资源状态冲突"),

    /** 触发限流 */
    TOO_MANY_REQUESTS(42900, "请求过于频繁"),

    /** 服务端未预期的错误，对外不暴露细节 */
    SYSTEM_ERROR(50000, "系统内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 是否为成功码。
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

}
