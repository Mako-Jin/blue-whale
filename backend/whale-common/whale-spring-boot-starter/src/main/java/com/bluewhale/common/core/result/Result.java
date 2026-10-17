package com.bluewhale.common.core.result;

import com.bluewhale.common.core.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体
 *
 * @param <T> 响应数据的类型
 * @author BlueWhale
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码 */
    private int code;

    /** 提示消息 */
    private String message;

    /** 响应数据 */
    private T data;

    /**
     * 构建无数据的成功响应
     *
     * @return 成功响应，数据为 {@code null}
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 构建携带数据的成功响应
     *
     * @param data 响应数据
     * @return 成功响应
     */
    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setCode(ErrorCode.SUCCESS.getCode());
        result.setMessage(ErrorCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    /**
     * 构建失败响应
     *
     * @param code 错误码
     * @param message 错误消息
     * @return 失败响应
     */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /**
     * 以错误码枚举构建失败响应
     *
     * @param errorCode 错误码枚举
     * @return 失败响应
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }
}
