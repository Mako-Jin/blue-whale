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
    }

    @Getter
    @Setter
    public static class Login {

        /** 连续失败多少次后锁定账号 */
        private int maxFailedAttempts = 5;

        /** 锁定时长 */
        private Duration lockDuration = Duration.ofMinutes(15);
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

}
