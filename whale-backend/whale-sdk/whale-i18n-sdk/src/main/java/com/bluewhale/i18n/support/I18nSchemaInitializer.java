package com.bluewhale.i18n.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 国际化表启动初始化器
 *
 * <p>在服务自身数据源上执行幂等 DDL（{@code CREATE TABLE IF NOT EXISTS}），
 * 让每个服务无需人工建表即可拥有独立的 {@code sys_i18n_message}。
 * 建表失败不阻断服务启动，文案会自然回退到属性文件。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class I18nSchemaInitializer implements ApplicationRunner {

    /**
     * 国际化表名
     */
    static final String TABLE_NAME = "sys_i18n_message";

    private final DataSource dataSource;

    private final JdbcTemplate jdbcTemplate;

    private final Resource ddlResource;

    /**
     * 构造初始化器
     *
     * @param dataSource  服务数据源
     * @param ddlResource 建表脚本
     */
    public I18nSchemaInitializer(DataSource dataSource, Resource ddlResource) {
        this(dataSource, ddlResource, new JdbcTemplate(dataSource));
    }

    /**
     * 构造初始化器（允许注入 JdbcTemplate，便于单元测试）
     *
     * @param dataSource   服务数据源
     * @param ddlResource  建表脚本
     * @param jdbcTemplate JDBC 模板
     */
    I18nSchemaInitializer(DataSource dataSource, Resource ddlResource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.ddlResource = ddlResource;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Spring 启动完成后回调，执行幂等建表
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        initialize();
    }

    /**
     * 执行幂等建表
     */
    public void initialize() {
        String ddl = readDdl();
        if (ddl == null) {
            return;
        }
        try {
            jdbcTemplate.execute(ddl);
            log.info("[whale-i18n] 国际化表已就绪：{}", TABLE_NAME);
        } catch (RuntimeException ex) {
            if (tableExists()) {
                log.info("[whale-i18n] 国际化表已存在，跳过自动初始化：{}", TABLE_NAME);
            } else {
                log.warn("[whale-i18n] 自动创建国际化表失败，文案将回退属性文件：{}", ex.getMessage());
            }
        }
    }

    /**
     * 读取建表脚本
     *
     * @return DDL 文本；读取失败返回 null
     */
    private String readDdl() {
        try (InputStream inputStream = ddlResource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("[whale-i18n] 读取建表脚本失败，跳过自动初始化：{}", ex.getMessage());
            return null;
        }
    }

    /**
     * 通过元数据判断表是否存在
     *
     * @return 存在返回 true
     */
    boolean tableExists() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tables = metaData.getTables(connection.getCatalog(), null, TABLE_NAME, new String[]{"TABLE"})) {
                return tables.next();
            }
        } catch (SQLException ex) {
            log.debug("[whale-i18n] 查询表元数据失败：{}", ex.getMessage());
            return false;
        }
    }
}
