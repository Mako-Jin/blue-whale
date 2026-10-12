package com.bluewhale.sts.auth.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 口令强度策略 —— 校验"新设口令"的形态是否满足要求。
 *
 * <p><b>职责边界：只校验形态，不判断"是否弱口令"。</b>
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

    public PasswordPolicy(AuthProperties properties) {
        AuthProperties.Password config = properties.getPassword();
        this.minLength = config.getMinLength();
        this.requireUppercase = config.isRequireUppercase();
        this.requireLowercase = config.isRequireLowercase();
        this.requireDigit = config.isRequireDigit();
        this.requireSpecial = config.isRequireSpecial();
        this.historyCount = config.getHistoryCount();
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

}
