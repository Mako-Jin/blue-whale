package com.bluewhale.sts.auth.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 权限域配置，前缀 {@code bluewhale.auth}。
 *
 * <p>依据 {@code docs/decisions/0003} 的配置分域：**密钥级配置（JWT 密钥）必须来自
 * 环境变量或密钥管理系统，不进版本库**；而 TTL、迭代次数这类"策略参数"属于业务运行期配置，
 * 给合理默认值即可。因此 {@link Jwt#getSecret()} 在 {@code application.yml} 里写成
 * {@code ${BW_JWT_SECRET:...}}，本地有默认值、生产靠环境变量覆盖。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bluewhale.auth")
public class AuthProperties {

    private final Jwt jwt = new Jwt();

    private final Password password = new Password();

    private final Login login = new Login();

    private final SeedAdmin seedAdmin = new SeedAdmin();

    private final SeedAuditor seedAuditor = new SeedAuditor();

    @Getter
    @Setter
    public static class Jwt {

        /** HMAC 密钥。**生产必须通过 BW_JWT_SECRET 注入**，长度建议不低于 64 字节 */
        private String secret;

        private String issuer = "bluewhale";

        private String audience = "bluewhale-api";

        /** 访问令牌有效期 —— 短，因为它是无状态的，撤销只能靠过期 */
        private Duration accessTtl = Duration.ofHours(2);

        /** 刷新令牌有效期 —— 长，因为它落库、可撤销、可轮换 */
        private Duration refreshTtl = Duration.ofDays(7);
    }

    @Getter
    @Setter
    public static class Password {

        /**
         * PBKDF2 迭代次数。
         *
         * <p>取值要在"抗暴力破解"与"登录延迟"之间权衡：太高会让登录接口成为
         * CPU 放大器（攻击者可用它做拒绝服务）。12 万次在现代 CPU 上约几十毫秒，
         * 是当前较常用的量级。**升级此值时不需要迁移存量**：算法标识与迭代次数
         * 都随哈希一起存储，校验按存储值走，登录成功后再按新值重算覆盖。
         */
        private int iterations = 120_000;

        /** 派生密钥长度（位） */
        private int keyLengthBits = 256;

        /** 盐长度（字节） */
        private int saltLengthBytes = 16;

        // ===== 口令强度策略（{@link PasswordPolicy} 逐条读取）=====
        //
        // 这些项属于 ADR-0003 所说的"业务运行期配置"：改了不用重新编译、不用迁移存量，
        // 存量口令只在下次改密时按新规则校验（**不会**因此失效，也不强制重置）。
        //
        // **默认值刻意偏保守**：长度 8 + 三类字符。更严的规则（要求特殊字符、
        // 禁止重用历史口令）是最容易让用户把口令写在便签上的那类要求，
        // 应当由部署方按自己的合规基线显式打开，而不是由框架替它决定。

        /** 口令最小长度 */
        private int minLength = 8;

        /** 是否必须包含大写字母 */
        private boolean requireUppercase = true;

        /** 是否必须包含小写字母 */
        private boolean requireLowercase = true;

        /** 是否必须包含数字 */
        private boolean requireDigit = true;

        /** 是否必须包含特殊字符（非字母、非数字） */
        private boolean requireSpecial = false;

        /**
         * 保留多少个历史口令禁止重用；{@code 0} 关闭该检查。
         *
         * <p>只对新设口令校验收，**不追溯存量**：开启此开关不会让任何既有账号失效。
         */
        private int historyCount = 5;
    }

    @Getter
    @Setter
    public static class Login {

        /** 连续失败多少次后锁定账号 */
        private int maxFailedAttempts = 5;

        /**
         * **首次**锁定的时长。
         *
         * <p>它同时是指数退避的基数：第 n 次被锁定时长为
         * {@code lockDuration × lockGrowthFactor^(n-1)}，并以 {@link #maxLockDuration} 封顶。
         */
        private Duration lockDuration = Duration.ofMinutes(5);

        /**
         * 每再被锁定一次，时长乘以此因子。
         *
         * <p><b>为什么不是固定时长：</b>固定时长对攻击者是"恒定的重试预算" ——
         * 每 15 分钟来 5 次，成本不随次数上升。退避让持续攻击的代价指数增长
         * （5 / 10 / 20 / 40 / 80 分钟…），而偶尔打错口令的合法用户第一次只需等 5 分钟。
         *
         * <p>设为 {@code 1.0} 即退化为固定时长（等同旧行为）。
         */
        private double lockGrowthFactor = 2.0;

        /** 退避时长的上限。达到后不再增长 */
        private Duration maxLockDuration = Duration.ofHours(24);
    }

    /**
     * 默认管理员种子数据。
     *
     * <p><b>为什么需要它：</b>建租户、建用户这些接口本身要求已认证，存在"先有鸡还是先有蛋"。
     * 必须有一条不依赖认证的引导路径，否则一套干净的库无法登录任何东西。
     *
     * <p><b>默认关闭，只在 h2（开发 / 演示）profile 打开</b>（见 {@code application-h2.yml}）。
     * 生产环境不得开启：它会在库里种下一个口令固定的管理员账号 ——
     * 这类"默认口令"是所有渗透测试的第一站。开启时本类会打一条 WARN 提醒。
     *
     * <p>更稳妥的生产做法是首次部署时由运维显式建号，或走审批流开通 ——
     * 那条路径不在本切片范围内。
     */
    @Getter
    @Setter
    public static class SeedAdmin {

        private boolean enabled = false;

        private String tenantCode = "default";

        private String tenantName = "默认租户";

        private String username = "admin";

        /** 仅用于开发演示。生产若开启种子，务必通过配置覆盖 */
        private String password = "Admin@12345";

        private String displayName = "系统管理员";
    }

    /**
     * 默认只读账号种子数据（开发 / 演示用）。
     *
     * <p><b>为什么还需要一个"只读"账号：</b>只有超级管理员时，"权限真的生效了吗"这个问题
     * 是验证不了的 —— 所有接口对唯一身份都放行，写错的权限标识、漏配的资源、
     * 前端把按钮显隐判反，症状都是"看起来很正常"。一个**有读、无写**的第二身份
     * 把这件事变成可观测的差异（列表看得见、新建按钮不出现、直接调接口得 403），
     * 这也是按钮级权限演示能落地的前提。
     *
     * <p>它只被授予 {@code auth:user:read} 与 {@code auth:tenant:read}，
     * 因此**不得**在它身上挂任何写权限 —— 一旦挂上，上面那句"把差异变成可观测"就失效了。
     *
     * <p>与 {@link SeedAdmin} 同样**默认关闭、只在 h2 profile 打开**：
     * 生产库里多一个口令固定的账号就是多一个入口。
     */
    @Getter
    @Setter
    public static class SeedAuditor {

        /** 是否种下只读账号。默认关闭，由 profile 打开 */
        private boolean enabled = false;

        /** 复用 {@link SeedAdmin#getTenantCode()} 指向的租户；种子数据必须落在同一个租户内 */
        private String username = "auditor";

        /** 仅用于开发演示。生产若开启种子，务必通过配置覆盖 */
        private String password = "Auditor@12345";

        private String displayName = "审计员";
    }

}
