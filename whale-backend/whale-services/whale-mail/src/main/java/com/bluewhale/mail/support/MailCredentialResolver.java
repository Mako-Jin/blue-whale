package com.bluewhale.mail.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.mail.entity.MailAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 邮箱凭据解析：把账号上的凭据来源还原成实际密码。
 *
 * <p><b>两种来源，按序取用</b>：</p>
 * <ol>
 *   <li>{@code secretRef} 有值 → 读部署侧注入的配置（外部托管的企业邮箱，口令不归平台管）</li>
 *   <li>否则 {@code secretCipher} 有值 → 解密（平台代开的邮箱，口令由服务端随机生成）</li>
 * </ol>
 *
 * <p>密码不落明文、不入请求体、不进日志。任何一步失败都快速报错，
 * 不做「空密码继续连接」的降级，否则会变成难排查的认证超时。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailCredentialResolver {

    /** 密码最小长度：过短基本可判定为占位值 */
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final Environment environment;

    private final MailSecretCipher secretCipher;

    /**
     * 解析账号密码
     *
     * @param account 邮箱账号
     * @return 明文密码（仅用于本次协议连接，禁止缓存与输出）
     */
    public String resolve(MailAccount account) {
        String ref = account.getSecretRef();
        if (StringUtils.hasText(ref)) {
            return resolveFromConfig(ref);
        }
        if (StringUtils.hasText(account.getSecretCipher())) {
            String password = secretCipher.decrypt(account.getSecretCipher());
            if (password.length() < MIN_PASSWORD_LENGTH) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "mail.credential.password-too-short",
                        "解密得到的邮箱口令过短，疑似密文损坏");
            }
            return password;
        }
        throw new BizException(ErrorCode.PARAM_ERROR,
                "mail.credential.not-configured",
                "邮箱账号未配置凭据（secretRef 与 secretCipher 均为空），无法建立连接");
    }

    /**
     * 从部署侧配置解析口令
     *
     * @param ref 配置键名
     * @return 明文密码
     */
    private String resolveFromConfig(String ref) {
        String password = environment.getProperty(ref);
        if (!StringUtils.hasText(password)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "凭据引用未解析到值：" + ref);
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR, "凭据值过短，疑似占位配置：" + ref);
        }
        return password;
    }
}
