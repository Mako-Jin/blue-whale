package com.bluewhale.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

/**
 * 数据源密码启动守卫（P1-2）。
 *
 * <p>各服务 yml 已不再为 {@code MYSQL_PASSWORD} 提供 {@code root} 之类的弱默认值。
 * 若服务配置了 {@code spring.datasource.url} 但密码为空，则在 Bean 装配阶段直接拒绝启动，
 * 并给出可直接定位问题与修复方式的中文报错，避免以空口令连库后才暴露连接失败。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class DataSourcePasswordGuard implements InitializingBean {

    /** 数据源地址配置项 */
    private static final String URL_PROPERTY = "spring.datasource.url";

    /** 数据源密码配置项 */
    private static final String PASSWORD_PROPERTY = "spring.datasource.password";

    /** 服务名配置项，用于在报错中标识具体服务 */
    private static final String APPLICATION_NAME_PROPERTY = "spring.application.name";

    /** 覆盖数据源密码所需的环境变量名 */
    private static final String PASSWORD_ENV_NAME = "MYSQL_PASSWORD";

    /** 密码为空时的拒绝原因，消息需能直接定位问题 */
    private static final String REJECT_MESSAGE_TEMPLATE = "服务 [%s] 的数据源密码为空，拒绝启动："
            + "spring.datasource.password 未配置（yml 中已移除默认弱口令）。"
            + "请通过环境变量 " + PASSWORD_ENV_NAME + " 显式设置数据源密码后重试。";

    private final Environment environment;

    /**
     * 构造启动守卫
     *
     * @param environment 应用环境，用于读取数据源配置
     */
    public DataSourcePasswordGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String url = environment.getProperty(URL_PROPERTY);
        if (url == null || url.isBlank()) {
            // 未配置数据源的服务（如纯网关/监控类应用）无需校验
            return;
        }
        String password = environment.getProperty(PASSWORD_PROPERTY);
        if (password == null || password.isBlank()) {
            String appName = environment.getProperty(APPLICATION_NAME_PROPERTY, "unknown");
            throw new IllegalStateException(String.format(REJECT_MESSAGE_TEMPLATE, appName));
        }
    }
}
