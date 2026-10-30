package com.bluewhale.auth.bootstrap;

import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;

/**
 * 内置 admin 出厂默认口令轮换器（T-03 去除默认管理员口令）。
 *
 * <p>背景：init.sql 的种子 admin（id=1）密文对应出厂默认明文 123456，属 P1 安全项。
 * 本轮换器在服务启动时（{@link ApplicationRunner}，最高优先级）按以下口径处理：</p>
 * <ul>
 *   <li>admin 口令密文等于出厂种子密文（常量来源 db/init.sql 内置超级管理员 INSERT）→
 *       生成随机口令（SecureRandom，16 位，含大小写与数字，剔除易混淆字符），
 *       经既有 {@link PasswordEncoder} BCrypt 编码后经 User 聚合落库，
 *       置 {@code must_change_password=1}，并将明文以醒目分隔框 WARN 日志打印一次
 *       （仅启动日志可见，请立即登录改密）；</li>
 *   <li>环境变量 {@code WHALE_AUTH_ADMIN_INIT_PASSWORD}（自动化环境用）设置时以其替代
 *       随机口令，同样置强制改密标记；非 dev profile 下长度不足 12 位时打 WARN 提示弱口令风险；</li>
 *   <li>已轮换过（密文非种子密文）→ 无操作，幂等可重复启动；</li>
 *   <li>开关 {@code whale.auth.admin-password.rotate-on-default}（默认 {@code true}）
 *       可整体关闭轮换行为（见 {@link AdminPasswordProperties}）。</li>
 * </ul>
 *
 * <p>实现遵循既有 DDD 分层：本类仅在 starter 组合根编排，落库走领域仓储端口
 * {@link UserRepository} 与 User 聚合行为（{@code changePassword}/{@code markMustChangePassword}），
 * 不直连 Mapper 亦不裸写 SQL。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminPasswordInitializer implements ApplicationRunner, Ordered {

    /** 内置超级管理员固定用户 ID（init.sql 种子约定） */
    static final long ADMIN_USER_ID = 1L;

    /** init.sql 种子 admin 的出厂默认 BCrypt 密文（明文 123456，来源 db/init.sql 内置超级管理员 INSERT） */
    static final String SEED_ADMIN_PASSWORD_CIPHER = "$2a$10$rzq21So0/Oj02kDlvSr2K.lWaqOwb6TzkgwhuGRKIRGnlJV.F/l8m";

    /** 自动化环境指定初始口令的环境变量名 */
    static final String ENV_ADMIN_INIT_PASSWORD = "WHALE_AUTH_ADMIN_INIT_PASSWORD";

    /** 非 dev profile 下环境变量指定口令的推荐最小长度 */
    static final int MIN_ENV_PASSWORD_LENGTH = 12;

    /** 随机口令长度 */
    private static final int PASSWORD_LENGTH = 16;

    /** 随机口令大写字母表（剔除易混淆的 I / O） */
    private static final String UPPER_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    /** 随机口令小写字母表（剔除易混淆的 l / o） */
    private static final String LOWER_ALPHABET = "abcdefghijkmnpqrstuvwxyz";
    /** 随机口令数字表（剔除易混淆的 0 / 1） */
    private static final String DIGIT_ALPHABET = "23456789";
    /** 三类字符合并字母表 */
    private static final String ALL_ALPHABET = UPPER_ALPHABET + LOWER_ALPHABET + DIGIT_ALPHABET;

    /** 随机源（JDK 自带，不引入新依赖） */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** User 聚合仓储端口（跨租户读取 admin 并经聚合落库） */
    private final UserRepository userRepository;

    /** 口令加密器（既有 BCrypt encoder bean） */
    private final PasswordEncoder passwordEncoder;

    /** 轮换开关配置 */
    private final AdminPasswordProperties properties;

    /** 环境抽象：读取环境变量与激活 profile */
    private final Environment environment;

    /**
     * 启动时执行：检测内置 admin 是否仍为出厂默认口令，是则轮换并打印一次初始口令
     *
     * @param args 应用启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isRotateOnDefault()) {
            log.info("内置 admin 默认口令轮换已关闭（whale.auth.admin-password.rotate-on-default=false），跳过");
            return;
        }
        // 启动阶段无租户上下文，sys_user 归属首页租户，按全局 userId 跨租户定位内置 admin
        User admin = userRepository.findByIdAcrossTenants(ADMIN_USER_ID).orElse(null);
        if (admin == null) {
            log.info("未找到内置 admin 用户（id=1），跳过默认口令轮换");
            return;
        }
        if (!SEED_ADMIN_PASSWORD_CIPHER.equals(admin.getPassword().value())) {
            // 已轮换过或部署时已自改口令：幂等无操作
            return;
        }
        String initPassword = resolveInitPassword();
        admin.changePassword(EncryptedPassword.of(passwordEncoder.encode(initPassword)));
        admin.markMustChangePassword();
        userRepository.save(admin);
        printRotationBanner(admin.getUsername().value(), initPassword);
    }

    /**
     * 最高优先级：先于其余 ApplicationRunner 执行，保证后续启动逻辑使用的是已轮换口令
     *
     * @return 优先级（{@link Ordered#HIGHEST_PRECEDENCE}）
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 解析初始口令：环境变量 {@code WHALE_AUTH_ADMIN_INIT_PASSWORD} 优先（自动化环境用），
     * 未设置时生成随机口令
     *
     * @return 初始口令明文（仅用于编码与一次性打印，不落库明文）
     */
    private String resolveInitPassword() {
        String envPassword = environment.getProperty(ENV_ADMIN_INIT_PASSWORD);
        if (StringUtils.hasText(envPassword)) {
            log.info("检测到环境变量 {}，以其作为内置 admin 初始口令", ENV_ADMIN_INIT_PASSWORD);
            if (!isDevProfile() && envPassword.length() < MIN_ENV_PASSWORD_LENGTH) {
                log.warn("环境变量 {} 长度 {} 低于推荐下限 {}，存在弱口令风险，建议更换",
                        ENV_ADMIN_INIT_PASSWORD, envPassword.length(), MIN_ENV_PASSWORD_LENGTH);
            }
            return envPassword;
        }
        return generateRandomPassword();
    }

    /**
     * 判断当前是否激活 dev profile（用于环境变量口令的弱口令提示口径）
     *
     * @return 激活 dev profile 返回 true
     */
    private boolean isDevProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equals(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成随机口令：16 位，含大小写与数字，剔除易混淆字符（I O l o 0 1），
     * 并保证三类字符至少各出现一次后整体 Fisher-Yates 洗牌
     *
     * @return 随机口令明文
     */
    private static String generateRandomPassword() {
        StringBuilder builder = new StringBuilder(PASSWORD_LENGTH);
        builder.append(randomCharOf(UPPER_ALPHABET));
        builder.append(randomCharOf(LOWER_ALPHABET));
        builder.append(randomCharOf(DIGIT_ALPHABET));
        while (builder.length() < PASSWORD_LENGTH) {
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

    /**
     * 以醒目分隔框打印初始口令（WARN 级别，仅启动日志打印一次，提示首登改密）
     *
     * @param username 内置管理员用户名
     * @param initPassword 初始口令明文
     */
    private static void printRotationBanner(String username, String initPassword) {
        log.warn("======================================================================");
        log.warn("  检测到内置用户 {}（id={}）仍为 init.sql 出厂默认口令，已自动轮换", username, ADMIN_USER_ID);
        log.warn("  初始口令: {}", initPassword);
        log.warn("  该口令仅此次启动日志打印一次，请立即使用其登录并修改口令");
        log.warn("  （登录响应 mustChangePassword=true，改密成功后标记自动清除）");
        log.warn("======================================================================");
    }
}
