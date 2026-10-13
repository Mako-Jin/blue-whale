package com.bluewhale.sts.auth.common;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 口令策略 —— {@code bluewhale.auth.password} 下那些策略参数的**唯一读取处**：
 * 强度（{@link #violations}）、防重用的覆盖范围（{@link #historyCount}）、
 * 有效期（{@link #expiryOf} 与 {@link #isExpired}）。
 *
 * <p><b>职责边界：就 {@link #violations} 而言，它只校验形态，不判断"是否弱口令"。</b>
 * 字典比对（{@code 123456}、{@code password}、{@code admin123}）需要一份词表与额外的判定语义，
 * 不在本切片的范围内；本类回答的是"长度够不够、字符类别缺哪几类"。
 *
 * <p><b>与参照实现 {@code SystemTerminator} 的取舍：</b>
 * 它有 {@code PasswordComplexityEnums}（LOW / MEDIUM / HIGH）配合
 * {@code PasswordPolicy.validate(...)}。**思路保留，形状改掉**，两点原因：
 * <ol>
 *   <li><b>不设"复杂度等级"这一层间接。</b>"高"到底是要 12 位还是要特殊字符，
 *       只能回查枚举；而"必须含特殊字符、但可以不要求大写"这类组合，等级根本表达不了，
 *       只能再加一级枚举值。直接配置"长度 + 需要哪几类字符"更精确，
 *       且与 {@link AuthProperties} 里其它策略参数同一形态。</li>
 *   <li><b>返回全部未满足项，而不是 boolean。</b>{@code validate()} 返回 boolean 时，
 *       调用方只够回一句"口令不符合要求"，用户得反复试才知道缺什么；
 *       逐条列出缺失项才能给出可行指引。</li>
 * </ol>
 *
 * <p><b>它不做哈希、不落库</b>（那是 {@code common/codec/PasswordHasher}），
 * 也**不查历史口令是否重用** —— 那要读库，属于调用方（{@code UserService}）的编排。
 * 本类是纯函数式的：同一入参永远得到同一结论，因此可以脱离 Spring 直接测。
 */
public class PasswordPolicy {

    /** 与 {@code UserSaveRequest} 的 {@code @Size(max = 128)} 保持一致，避免两处上限各说各话 */
    private static final int MAX_LENGTH = 128;

    private final int minLength;
    private final boolean requireUppercase;
    private final boolean requireLowercase;
    private final boolean requireDigit;
    private final boolean requireSpecial;
    private final int historyCount;
    private final int expireDays;

    public PasswordPolicy(AuthProperties properties) {
        AuthProperties.Password config = properties.getPassword();
        this.minLength = config.getMinLength();
        this.requireUppercase = config.isRequireUppercase();
        this.requireLowercase = config.isRequireLowercase();
        this.requireDigit = config.isRequireDigit();
        this.requireSpecial = config.isRequireSpecial();
        this.historyCount = config.getHistoryCount();
        this.expireDays = config.getExpireDays();
    }

    /**
     * 逐条校验，返回**全部**未满足的要求；空列表表示通过。
     *
     * <p>刻意不用 boolean、也不在第一条就返回：一次把缺失项全说出来，
     * 用户改一遍就能过，而不是"改一处、报一处"。这也是为什么它返回的是
     * 人话短语（"至少一个大写字母"）而不是枚举 —— 这些短语会直接进入错误消息。
     *
     * <p>大小写判定用 {@link Character#isUpperCase} 而不是正则：中文、重音字母、
     * 全角字符各有各的 Unicode 类别，正则的 {@code [A-Z]} 会把这些全都漏掉，
     * 而 {@code Character} 的判定按 Unicode 属性走，更接近"用户以为的大写"。
     * 代价是中文计入"字母"（{@code isLetter} 为真）—— 对特殊字符的判定无影响，
     * 因为那一项用的是 {@code !isLetterOrDigit}。
     */
    public List<String> violations(String rawPassword) {
        List<String> missing = new ArrayList<>();

        if (rawPassword == null || rawPassword.isEmpty()) {
            missing.add("不能为空");
            return missing;
        }
        if (rawPassword.length() < minLength) {
            missing.add("长度至少 " + minLength + " 位");
        }
        if (requireUppercase && rawPassword.chars().noneMatch(Character::isUpperCase)) {
            missing.add("至少一个大写字母");
        }
        if (requireLowercase && rawPassword.chars().noneMatch(Character::isLowerCase)) {
            missing.add("至少一个小写字母");
        }
        if (requireDigit && rawPassword.chars().noneMatch(Character::isDigit)) {
            missing.add("至少一个数字");
        }
        if (requireSpecial && rawPassword.chars().noneMatch(codePoint -> !Character.isLetterOrDigit(codePoint))) {
            missing.add("至少一个特殊字符");
        }
        return missing;
    }

    /**
     * 当前策略的完整描述，供错误消息与接口文档使用。
     *
     * <p>它存在的意义是**让提示永远跟着配置走**：策略参数改了，提示语自动跟着改，
     * 不会出现"配置要求 12 位、提示还写着 8 位"这类只在生产才被发现的错位。
     */
    public String describe() {
        StringBuilder description = new StringBuilder("长度 " + minLength + "~" + MAX_LENGTH + " 位");

        List<String> classes = new ArrayList<>();
        if (requireUppercase) {
            classes.add("大写字母");
        }
        if (requireLowercase) {
            classes.add("小写字母");
        }
        if (requireDigit) {
            classes.add("数字");
        }
        if (requireSpecial) {
            classes.add("特殊字符");
        }
        if (!classes.isEmpty()) {
            description.append("，且至少包含 ").append(String.join("、", classes)).append(" 各一类");
        }
        return description.toString();
    }

    /** 需要记住多少个历史口令禁止重用；{@code 0} 表示关闭该项检查 */
    public int historyCount() {
        return historyCount;
    }

    /**
     * 在 {@code now} 这个时刻设下的口令，什么时候失效；{@code null} 表示**不失效**。
     *
     * <p>它由 {@code PasswordChanger} 换算成 {@code password_expires_at} 落库 ——
     * 这个算术留在这里而不是散在调用点，理由与 {@code violations} 一样：
     * "有效期多长"是一个策略问题，它不该有两个版本。
     */
    public Long expiryOf(long now) {
        if (expireDays <= 0) {
            return null;
        }
        return now + Duration.ofDays(expireDays).toMillis();
    }

    /**
     * 这个口令是不是已经过期了 —— 登录与刷新两处**必须问同一个问题**。
     *
     * <p><b>第一步先看策略有没有开，而不是先看那个时刻。</b>这不是防御性编程，
     * 而是这个功能唯一的"关掉"实现方式：清残留做不到（
     * {@code PasswordChanger#assignNewPassword} 里说明了原因 ——
     * {@code updateById} 默认跳过 null 字段，所以把那一列写回 NULL 是个不会落库的动作），
     * 而且清残留最多只覆盖"关掉策略之后又改过密的人"，其余人的旧时刻一直都在。
     * 判断这一侧先看策略，效果是**配置一改完立刻对所有人生效、不需要动一行数据**。
     *
     * <p>{@code null} 时刻同样表示不过期，覆盖两种正常情形：策略启用之前就存在的
     * 存量账号（不追溯，与 {@code historyCount} 同一条原则），以及建号时策略还没打开。
     *
     * <p>两个调用点共用这一个判断，理由是它们是同一个问题的两个入口：
     * 分开各写一份，将来改口径（比如加宽限期）就只可能改到其中一处，
     * 而症状是"登录说没过期、刷新说过期"—— 一个只在特定路径上出现的矛盾。
     */
    public boolean isExpired(Long expiresAt, long now) {
        if (expireDays <= 0) {
            return false;
        }
        return expiresAt != null && expiresAt <= now;
    }

}
