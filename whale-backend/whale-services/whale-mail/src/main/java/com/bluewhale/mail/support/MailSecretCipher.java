package com.bluewhale.mail.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.security.crypto.ConfigEncryptionSettings;
import com.bluewhale.common.security.crypto.ConfigValueCipher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 平台代开邮箱的口令加解密。
 *
 * <p>与 {@code secret_ref}（部署侧注入）互补：代开邮箱的口令由服务端随机生成，
 * 服务端必须能读回才能登录 IMAP，所以只能落库——但落的是密文。</p>
 *
 * <p><b>三个约束</b>：</p>
 * <ul>
 *   <li>复用 {@code whale-config-crypto} 的 {@code ENC(...)} 约定，不引入第二套密钥体系；
 *       密钥来自环境变量 {@link ConfigEncryptionSettings#ENV_KEY}，不进库、不进镜像。</li>
 *   <li>密钥缺失时<b>必须失败</b>，不允许明文落库或空口令降级——那等于把口令写进备份文件。</li>
 *   <li>报错只带列名与配置项名，绝不回显密文与密钥。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Component
public class MailSecretCipher {

    /** 报错定位用的属性名（不是真实配置键，仅用于日志与异常文案） */
    private static final String PROPERTY_NAME = "whale.mail.credential.secret-cipher";

    /** 运行环境（读取加密密钥） */
    private final Environment environment;

    /** 加密器（懒加载：未开启代开时不需要密钥，避免无谓的启动失败） */
    private volatile ConfigValueCipher cipher;

    /**
     * 构造器注入
     *
     * @param environment 运行环境
     */
    public MailSecretCipher(Environment environment) {
        this.environment = environment;
    }

    /**
     * 加密口令
     *
     * @param plainText 明文口令
     * @return {@code ENC(...)} 形式的密文
     */
    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mail.credential.password-empty", "待加密的邮箱口令为空");
        }
        try {
            return delegate().encrypt(plainText);
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "mail.credential.encrypt-failed",
                    "邮箱口令加密失败：" + e.getMessage(), e.getMessage());
        }
    }

    /**
     * 解密口令
     *
     * @param cipherText {@code ENC(...)} 形式的密文
     * @return 明文口令（仅用于本次协议连接）
     */
    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mail.credential.cipher-empty", "邮箱凭据密文为空");
        }
        try {
            Object plain = delegate().decryptIfNeeded(PROPERTY_NAME, cipherText);
            if (!(plain instanceof String text) || !StringUtils.hasText(text)) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "mail.credential.decrypt-empty", "邮箱凭据解密结果为空");
            }
            return text;
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "mail.credential.decrypt-failed",
                    "邮箱凭据解密失败，请检查 " + ConfigEncryptionSettings.ENV_KEY + " 是否与加密时一致",
                    ConfigEncryptionSettings.ENV_KEY);
        }
    }

    /**
     * 懒加载加密器：密钥缺失时抛出可定位的业务异常，而不是静默降级
     *
     * @return 加密器
     */
    private ConfigValueCipher delegate() {
        ConfigValueCipher current = cipher;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (cipher == null) {
                String key = environment.getProperty(ConfigEncryptionSettings.ENV_KEY);
                if (!StringUtils.hasText(key)) {
                    throw new BizException(ErrorCode.SYSTEM_ERROR, "mail.credential.key-not-configured",
                            "未配置邮箱凭据加密密钥 " + ConfigEncryptionSettings.ENV_KEY
                                    + "：平台代开邮箱需要它加密口令，禁止明文落库", ConfigEncryptionSettings.ENV_KEY);
                }
                try {
                    cipher = new ConfigValueCipher(ConfigEncryptionSettings.of(key));
                } catch (RuntimeException e) {
                    throw new BizException(ErrorCode.SYSTEM_ERROR, "mail.credential.key-unavailable",
                            "邮箱凭据加密密钥不可用（" + ConfigEncryptionSettings.ENV_KEY + "）：" + e.getMessage(),
                            ConfigEncryptionSettings.ENV_KEY, e.getMessage());
                }
            }
            return cipher;
        }
    }
}
