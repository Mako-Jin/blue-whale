package com.bluewhale.sts.common.basic.model;

import com.bluewhale.sts.common.basic.enums.ResultCode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应包装 —— 所有 HTTP 接口的返回体都是这个形状。
 *
 * <pre>
 * { "code": 0, "message": "成功", "data": {...}, "timestamp": 1759999999999 }
 * </pre>
 *
 * <p><b>为什么失败也返回 HTTP 200：</b>HTTP 状态码描述的是"传输层是否成功送达"，
 * 业务失败（余额不足、重复提交）在传输层是成功的。若混用状态码表达业务语义，
 * 网关、CDN、重试中间件会把业务失败当成传输故障去重试，反而制造重复扣款一类的故障。
 * 因此约定：<b>业务结果只由 {@code code} 表达</b>，前端统一判断 {@code code === 0}。
 *
 * <p>真正属于传输层的失败（401/403/404/5xx）仍由框架按标准返回，不套此包装。
 *
 * @param <T> 业务数据类型
 */
@Getter
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务结果码，0 表示成功 */
    private final int code;

    /** 提示信息，可直接展示给用户 */
    private final String message;

    /** 业务数据，失败时为 {@code null} */
    private final T data;

    /** 服务端时间戳（毫秒），便于排查前后端时序与缓存问题 */
    private final long timestamp;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> R<T> ok() {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 用自定义文案覆盖枚举里的默认文案，错误码仍取自枚举 */
    public static <T> R<T> fail(ResultCode resultCode, String message) {
        return new R<>(resultCode.getCode(), message, null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    /**
     * 是否为成功码。
     *
     * <p>标 {@link JsonIgnore}：Jackson 会把 {@code isXxx()} 视作 getter，
     * 序列化出一个名为 {@code success} 的字段 —— 凭空给契约里多塞一个前端没预期的键。
     * 这类多余字段本身无害，但会诱导前端写出 {@code if (res.success)} 这种
     * 与 {@code code} 并存的第二套判断标准，两套标准迟早不一致。
     */
    @JsonIgnore
    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }

    @Override
    public String toString() {
        return "R{code=" + code + ", message='" + message + "', data=" + data + '}';
    }

}
