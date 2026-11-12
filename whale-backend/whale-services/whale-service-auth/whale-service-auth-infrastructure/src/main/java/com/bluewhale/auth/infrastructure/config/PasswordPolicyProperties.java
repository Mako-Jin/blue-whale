package com.bluewhale.auth.infrastructure.config;

import com.bluewhale.auth.application.port.PasswordPolicyConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 口令策略配置绑定（REQ-SEC-021 / REQ-SEC-023），实现应用层 {@link PasswordPolicyConfig} 端口。
 *
 * <p>默认值即计划确认的口径：90 天有效期、提前 7 天提醒、最近 5 次不可复用、最小长度 12
 * 且必须含大小写字母、数字与特殊字符。{@code max-age-days<=0} 可临时关闭到期强制（应急开关）。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.auth.password")
public class PasswordPolicyProperties implements PasswordPolicyConfig {

    /** 口令有效期（天）；小于等于 0 表示不强制到期 */
    private int maxAgeDays = 90;

    /** 到期前提醒天数 */
    private int warnDays = 7;

    /** 口令历史保留条数（最近 N 次不可复用） */
    private int historySize = 5;

    /** 最小长度 */
    private int minLength = 12;

    /** 最大长度（与数据库 VARCHAR(100) 的 BCrypt 密文容量无关，仅限制用户输入） */
    private int maxLength = 64;

    /** 是否要求大写字母 */
    private boolean requireUpper = true;

    /** 是否要求小写字母 */
    private boolean requireLower = true;

    /** 是否要求数字 */
    private boolean requireDigit = true;

    /** 是否要求特殊字符 */
    private boolean requireSpecial = true;

    /** 新建/更新/重置口令后是否强制目标用户下次登录改密 */
    private boolean forceChangeOnCreate = true;
}
