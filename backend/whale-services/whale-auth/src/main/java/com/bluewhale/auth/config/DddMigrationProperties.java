package com.bluewhale.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * whale-auth DDD 迁移灰度开关配置（AUTH-16 / REQ-ARCH-001 / T-DDD-10）。
 *
 * <p>{@code whale.auth.ddd.{user,role,menu,permission,dept}} 分别控制五个上下文的
 * Controller 分流与读路径实现，<b>默认全部 {@code false}</b>：关闭时服务层走既有 Mapper 路径
 * （与迁移前逐字节一致），开启时改走 {@code application} 层 AppService / {@code domain} 仓储。
 * 开关变更秒级生效，出问题即刻关闭即回旧实现（秒级回滚）。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.auth.ddd")
public class DddMigrationProperties {

    /** User 上下文（含 ABAC 用户属性）是否改走应用服务（默认关闭） */
    private boolean user = false;

    /** Role 上下文是否改走 DDD 仓储读路径（默认关闭） */
    private boolean role = false;

    /** Menu 上下文是否改走 DDD 仓储读路径（默认关闭） */
    private boolean menu = false;

    /** Permission 上下文是否改走 DDD 仓储读路径（默认关闭） */
    private boolean permission = false;

    /** Dept 上下文是否改走 DDD 仓储读路径（默认关闭） */
    private boolean dept = false;
}
