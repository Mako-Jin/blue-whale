package com.bluewhale.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DbTypeParser} 单元测试（REQ-ARCH-002 多数据库适配）：
 * 五种合法取值映射、非法取值 fail fast、未配置默认 MYSQL。
 *
 * @author BlueWhale
 */
class DbTypeParserTest {

    @Test
    @DisplayName("parse：mysql 解析为 MYSQL")
    void shouldParseMysql() {
        assertEquals(DbType.MYSQL, DbTypeParser.parse("mysql"));
    }

    @Test
    @DisplayName("parse：dm 解析为 DM（达梦）")
    void shouldParseDm() {
        assertEquals(DbType.DM, DbTypeParser.parse("dm"));
    }

    @Test
    @DisplayName("parse：kingbase 解析为 KINGBASE_ES（人大金仓）")
    void shouldParseKingbase() {
        assertEquals(DbType.KINGBASE_ES, DbTypeParser.parse("kingbase"));
    }

    @Test
    @DisplayName("parse：opengauss 解析为 OPENGAUSS")
    void shouldParseOpengauss() {
        assertEquals(DbType.OPENGAUSS, DbTypeParser.parse("opengauss"));
    }

    @Test
    @DisplayName("parse：oceanbase 按 MySQL 兼容模式解析为 MYSQL")
    void shouldParseOceanbaseAsMysql() {
        assertEquals(DbType.MYSQL, DbTypeParser.parse("oceanbase"));
    }

    @Test
    @DisplayName("parse：大小写不敏感且忽略首尾空白")
    void shouldBeCaseInsensitiveAndTrimmed() {
        assertEquals(DbType.DM, DbTypeParser.parse(" DM "));
        assertEquals(DbType.KINGBASE_ES, DbTypeParser.parse("KingBase"));
    }

    @Test
    @DisplayName("parse：非法取值抛 IllegalArgumentException 并列出全部合法取值")
    void shouldFailFastOnIllegalValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> DbTypeParser.parse("oracle"));

        assertTrue(exception.getMessage().contains("oracle"));
        assertTrue(exception.getMessage().contains("mysql"));
        assertTrue(exception.getMessage().contains("dm"));
        assertTrue(exception.getMessage().contains("kingbase"));
        assertTrue(exception.getMessage().contains("opengauss"));
        assertTrue(exception.getMessage().contains("oceanbase"));
    }

    @Test
    @DisplayName("parse：null/空白视为未配置，默认解析为 MYSQL")
    void shouldDefaultToMysqlWhenAbsent() {
        assertEquals(DbType.MYSQL, DbTypeParser.parse(null));
        assertEquals(DbType.MYSQL, DbTypeParser.parse(""));
        assertEquals(DbType.MYSQL, DbTypeParser.parse("   "));
    }

    @Test
    @DisplayName("parse：DatabaseProperties 默认 type=mysql，与历史写死 MYSQL 行为一致")
    void shouldKeepDefaultBehaviorWithPropertiesDefault() {
        DatabaseProperties properties = new DatabaseProperties();

        assertEquals("mysql", properties.getType());
        assertEquals(DbType.MYSQL, DbTypeParser.parse(properties.getType()));
    }
}
