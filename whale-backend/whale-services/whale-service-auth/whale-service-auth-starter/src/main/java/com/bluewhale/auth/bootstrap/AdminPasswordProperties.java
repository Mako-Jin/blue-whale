package com.bluewhale.auth.bootstrap;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内置 admin 出厂默认口令轮换配置（T-03 去除默认管理员口令）。
 *
 * <p>{@code whale.auth.admin-password.rotate-on-default}：启动时检测内置 admin（id=1）
 * 是否仍为 init.sql 出厂种子密文，是则自动轮换为随机口令（或环境变量
 * {@code WHALE_AUTH_ADMIN_INIT_PASSWORD} 指定值）并置 {@code must_change_password=1}，
 * 默认开启；置为 {@code false} 可整体关闭该轮换行为。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.auth.admin-password")
public class AdminPasswordProperties {

    /** 检测到出厂默认口令时是否自动轮换（默认开启） */
    private boolean rotateOnDefault = true;
}
