package com.bluewhale.auth.application.security;

import com.bluewhale.common.core.exception.BizException;

/**
 * 凭据校验失败异常（Wave 6.27）
 *
 * <p>与 {@link BizException} 的 401 语义兼容（对外仍是「用户名或密码错误」），
 * 但类型上可区分「口令错」与「账号已停用」：只有前者计入登录失败次数，
 * 避免把「账号被停用」也算成爆破尝试。</p>
 *
 * @author BlueWhale
 */
public class InvalidCredentialException extends BizException {

    private static final long serialVersionUID = 1L;

    /** 对外错误码：与既有 401 保持一致，前端无需改动 */
    public static final int CODE = 401;

    /**
     * 构造凭据校验失败异常
     *
     * @param message 对外提示文案（不区分账号不存在与口令错误，避免探测）
     */
    public InvalidCredentialException(String message) {
        super(CODE, message);
    }
}
