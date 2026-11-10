package com.bluewhale.auth.application.user.password.port;

/**
 * 口令策略配置端口（REQ-SEC-021 / REQ-SEC-023）：有效期、提醒窗口、历史次数与强度要求。
 *
 * <p>应用层只读全局口令策略；具体取值由 infrastructure 以 {@code whale.auth.password.*}
 * 配置绑定并注册为 Bean。{@code max-age-days<=0} 可临时关闭到期强制（应急开关）。</p>
 *
 * @author BlueWhale
 */
public interface PasswordPolicyConfig {

    /**
     * 口令有效期（天）
     *
     * @return 有效期天数；小于等于 0 表示不强制到期
     */
    int getMaxAgeDays();

    /**
     * 到期前提醒天数
     *
     * @return 提醒天数
     */
    int getWarnDays();

    /**
     * 口令历史保留条数
     *
     * @return 最近 N 次不可复用
     */
    int getHistorySize();

    /**
     * 最小长度
     *
     * @return 最小长度
     */
    int getMinLength();

    /**
     * 最大长度（与数据库 VARCHAR(100) 的 BCrypt 密文容量无关，仅限制用户输入）
     *
     * @return 最大长度
     */
    int getMaxLength();

    /**
     * 是否要求大写字母
     *
     * @return 要求返回 {@code true}
     */
    boolean isRequireUpper();

    /**
     * 是否要求小写字母
     *
     * @return 要求返回 {@code true}
     */
    boolean isRequireLower();

    /**
     * 是否要求数字
     *
     * @return 要求返回 {@code true}
     */
    boolean isRequireDigit();

    /**
     * 是否要求特殊字符
     *
     * @return 要求返回 {@code true}
     */
    boolean isRequireSpecial();

    /**
     * 新建/更新/重置口令后是否强制目标用户下次登录改密
     *
     * @return 强制改密返回 {@code true}
     */
    boolean isForceChangeOnCreate();

    /**
     * 到期提醒定时任务 cron
     *
     * @return cron 表达式（默认每日 02:00）
     */
    String getNoticeCron();
}
