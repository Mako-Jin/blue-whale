package com.bluewhale.common.mybatis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据库类型配置（REQ-ARCH-002 多数据库适配）。
 *
 * <p>统一前缀 {@code whale.db}，当前仅暴露 {@code type} 属性，用于切换 MyBatis-Plus
 * 分页插件方言；JDBC 驱动与连接信息按 vendor 由各服务环境注入，不在业务代码拼 URL。
 * 合法取值为 {@code mysql} / {@code dm} / {@code kingbase} / {@code opengauss} /
 * {@code oceanbase}，未配置时默认 {@code mysql}，行为与历史版本完全一致。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.db")
public class DatabaseProperties {

    /** 数据库类型，由 {@link DbTypeParser} 解析为 MyBatis-Plus 方言，非法取值启动期 fail fast */
    private String type = "mysql";
}
