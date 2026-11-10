package com.bluewhale.auth.application.user.password;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 临时口令生成器（REQ-SEC-023）：管理员重置口令时由系统生成一次性随机口令，
 * 保证包含大小写字母、数字与特殊字符，长度满足默认策略（16 位）。
 *
 * @author BlueWhale
 */
@Component
public class TemporaryPasswordGenerator {

    /** 仅使用 JDK 安全随机源 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 大写字母表（剔除易混淆 I / O） */
    private static final String UPPER_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    /** 小写字母表（剔除易混淆 l / o） */
    private static final String LOWER_ALPHABET = "abcdefghijkmnpqrstuvwxyz";
    /** 数字表（剔除易混淆 0 / 1） */
    private static final String DIGIT_ALPHABET = "23456789";
    /** 特殊字符表 */
    private static final String SPECIAL_ALPHABET = "!@#$%^&*";
    /** 三类字符合并字母表 */
    private static final String ALL_ALPHABET = UPPER_ALPHABET + LOWER_ALPHABET + DIGIT_ALPHABET + SPECIAL_ALPHABET;

    /** 临时口令长度 */
    private static final int PASSWORD_LENGTH = 16;

    /**
     * 生成一次性临时口令（默认长度 16）
     *
     * @return 临时口令明文（仅用于一次性展示，不落库）
     */
    public String generate() {
        return generate(0);
    }

    /**
     * 生成一次性临时口令，长度不低于策略最小值
     *
     * <p>先各取一个大写/小写/数字/特殊字符，再用全字母表补足长度并洗牌，避免位置规律。</p>
     *
     * @param minimumLength 策略要求的最小长度；小于默认长度时按默认长度生成
     * @return 临时口令明文（仅用于一次性展示，不落库）
     */
    public String generate(int minimumLength) {
        int length = Math.max(PASSWORD_LENGTH, minimumLength);
        StringBuilder builder = new StringBuilder(length);
        builder.append(randomCharOf(UPPER_ALPHABET));
        builder.append(randomCharOf(LOWER_ALPHABET));
        builder.append(randomCharOf(DIGIT_ALPHABET));
        builder.append(randomCharOf(SPECIAL_ALPHABET));
        while (builder.length() < length) {
            builder.append(randomCharOf(ALL_ALPHABET));
        }
        char[] chars = builder.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            char swapped = chars[i];
            chars[i] = chars[j];
            chars[j] = swapped;
        }
        return new String(chars);
    }

    /**
     * 从字母表随机取一个字符
     *
     * @param alphabet 字母表
     * @return 随机字符
     */
    private static char randomCharOf(String alphabet) {
        return alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length()));
    }
}
