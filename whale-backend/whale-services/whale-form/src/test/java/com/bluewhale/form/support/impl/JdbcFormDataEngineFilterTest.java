package com.bluewhale.form.support.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.form.dto.FormDataFilter;
import com.bluewhale.form.support.FormField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JdbcFormDataEngine} 动态字段条件检索的 SQL 构造与参数化绑定单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class JdbcFormDataEngineFilterTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final String TABLE = "form_data_fm_a_v1";
    private static final long TENANT = 1L;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JdbcFormDataEngine engine;

    /**
     * 构造被测引擎实例。
     */
    @BeforeEach
    void setUp() {
        engine = new JdbcFormDataEngine(jdbcTemplate);
    }

    /**
     * 构造字段定义桩对象。
     *
     * @param key 字段 key
     * @param type 存储类型
     * @return 字段定义
     */
    private static FormField field(String key, String type) {
        return new FormField(key, key, type, false, null);
    }

    /**
     * 构造过滤条件桩对象。
     *
     * @param field 字段 key
     * @param op 操作符
     * @param value 条件值
     * @return 过滤条件
     */
    private static FormDataFilter filter(String field, String op, Object value) {
        FormDataFilter filter = new FormDataFilter();
        filter.setField(field);
        filter.setOp(op);
        filter.setValue(value);
        return filter;
    }

    @Test
    @DisplayName("countByFilters：动态条件全部参数化绑定（含业务键/提交人）")
    void shouldBuildParameterizedCountSql() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(3L);
        List<FormField> fields = List.of(field("amount", "number"), field("name", "text"), field("stage", "select"));
        List<FormDataFilter> filters = List.of(
                filter("amount", "gte", 100),
                filter("name", "like", "张"),
                filter("stage", "in", List.of("new", "done")));

        long total = engine.countByFilters(TABLE, TENANT, fields, filters, "BK-1", "lisi");

        assertEquals(3L, total);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Long.class), paramsCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertTrue(sql.startsWith("SELECT COUNT(*) FROM `" + TABLE + "` WHERE `tenant_id` = ? AND `deleted` = 0"));
        assertTrue(sql.contains("`biz_key` = ?"));
        assertTrue(sql.contains("`submitter` = ?"));
        assertTrue(sql.contains("`amount` >= ?"));
        assertTrue(sql.contains("`name` LIKE ? ESCAPE '!'"));
        assertTrue(sql.contains("`stage` IN (?, ?)"));
        Object[] params = paramsCaptor.getValue();
        assertEquals(7, params.length);
        assertEquals(TENANT, params[0]);
        assertEquals("BK-1", params[1]);
        assertEquals("lisi", params[2]);
        assertEquals(new java.math.BigDecimal("100"), params[3]);
        assertEquals("%张%", params[4]);
        assertEquals("new", params[5]);
        assertEquals("done", params[6]);
    }

    @Test
    @DisplayName("selectIdsByFilters：按创建时间倒序并追加 limit 参数")
    void shouldBuildSelectIdsSql() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of(9L, 8L));
        List<FormField> fields = List.of(field("due_date", "date"));

        List<Long> ids = engine.selectIdsByFilters(TABLE, TENANT, fields,
                List.of(filter("due_date", "between", List.of("2026-01-01", "2026-12-31"))), null, null, 20);

        assertEquals(List.of(9L, 8L), ids);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), eq(Long.class), paramsCaptor.capture());
        assertTrue(sqlCaptor.getValue().startsWith("SELECT `id` FROM `" + TABLE + "`"));
        assertTrue(sqlCaptor.getValue().contains("`due_date` BETWEEN ? AND ?"));
        assertTrue(sqlCaptor.getValue().endsWith("ORDER BY `create_time` DESC, `id` DESC LIMIT ?"));
        assertEquals(4, paramsCaptor.getValue().length);
        assertEquals("2026-01-01", paramsCaptor.getValue()[1]);
        assertEquals(20, paramsCaptor.getValue()[3]);
    }

    @Test
    @DisplayName("操作符大小写不敏感（isnull → IS NULL）")
    void shouldNormalizeOperatorCase() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);

        long total = engine.countByFilters(TABLE, TENANT, List.of(field("memo", "text")),
                List.of(filter("memo", "isnull", null)), null, null);

        assertEquals(1L, total);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Long.class), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("`memo` IS NULL"));
    }

    @Test
    @DisplayName("字段不在 schema 白名单时抛参数错误（防注入）")
    void shouldRejectUnknownField() {
        BizException ex = assertThrows(BizException.class, () -> engine.countByFilters(TABLE, TENANT,
                List.of(field("name", "text")), List.of(filter("name` OR 1=1 --", "eq", "x")), null, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("类型与操作符不匹配时抛参数错误")
    void shouldRejectInvalidOperatorForType() {
        BizException ex = assertThrows(BizException.class, () -> engine.countByFilters(TABLE, TENANT,
                List.of(field("name", "text")), List.of(filter("name", "gt", "a")), null, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("不支持操作符"));
    }

    @Test
    @DisplayName("countByFilters：empty/notEmpty 别名归一为 IS NULL / IS NOT NULL")
    void shouldAcceptLinkageNullAliases() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);

        engine.countByFilters(TABLE, TENANT, List.of(field("name", "text")),
                List.of(filter("name", "empty", null)), null, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Long.class), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("`name` IS NULL"));
    }

    @Test
    @DisplayName("日期不存在时抛参数错误")
    void shouldRejectNonExistentDateValue() {
        BizException ex = assertThrows(BizException.class, () -> engine.countByFilters(TABLE, TENANT,
                List.of(field("day", "date")), List.of(filter("day", "eq", "2024-02-31")), null, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("日期值不存在"));
    }
}
