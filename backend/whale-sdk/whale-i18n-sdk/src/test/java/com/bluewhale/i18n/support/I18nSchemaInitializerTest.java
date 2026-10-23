package com.bluewhale.i18n.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link I18nSchemaInitializer} 启动建表单元测试
 *
 * @author BlueWhale
 */
class I18nSchemaInitializerTest {

    private static final String DDL_LOCATION = "db/i18n/mysql/sys_i18n_message.sql";

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private final DataSource dataSource = mock(DataSource.class);

    @Test
    @DisplayName("initialize：读取脚本并执行建表 DDL")
    void shouldExecuteDdl() {
        I18nSchemaInitializer initializer = new I18nSchemaInitializer(dataSource,
                new ClassPathResource(DDL_LOCATION), jdbcTemplate);

        initializer.initialize();

        verify(jdbcTemplate).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS sys_i18n_message"));
    }

    @Test
    @DisplayName("initialize：建表异常且表已存在时不抛出")
    void shouldSkipWhenTableAlreadyExists() throws SQLException {
        I18nSchemaInitializer initializer = new I18nSchemaInitializer(dataSource,
                new ClassPathResource(DDL_LOCATION), jdbcTemplate);
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("exists"))
                .when(jdbcTemplate).execute(anyString());
        mockTableExists(true);

        assertDoesNotThrow(initializer::initialize);
    }

    @Test
    @DisplayName("initialize：建表异常且表不存在时仅告警不抛出")
    void shouldWarnWhenTableMissing() throws SQLException {
        I18nSchemaInitializer initializer = new I18nSchemaInitializer(dataSource,
                new ClassPathResource(DDL_LOCATION), jdbcTemplate);
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("failed"))
                .when(jdbcTemplate).execute(anyString());
        mockTableExists(false);

        assertDoesNotThrow(initializer::initialize);
    }

    @Test
    @DisplayName("initialize：脚本不存在时跳过执行")
    void shouldSkipWhenDdlMissing() {
        Resource missing = new ClassPathResource("db/i18n/mysql/not-exists.sql");
        I18nSchemaInitializer initializer = new I18nSchemaInitializer(dataSource, missing, jdbcTemplate);

        assertDoesNotThrow(initializer::initialize);
        verify(jdbcTemplate, never()).execute(anyString());
    }

    /**
     * 桩造表是否存在
     *
     * @param exists 是否存在
     * @throws SQLException 模拟 JDBC 异常
     */
    private void mockTableExists(boolean exists) throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet tables = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("bluewhale_auth");
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(anyString(), org.mockito.ArgumentMatchers.isNull(), anyString(),
                org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(tables);
        when(tables.next()).thenReturn(exists);
    }
}
