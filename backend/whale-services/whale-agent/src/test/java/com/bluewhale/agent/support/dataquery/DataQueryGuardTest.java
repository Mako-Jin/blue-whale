package com.bluewhale.agent.support.dataquery;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 只读 SQL 守卫单测：白名单、写语句、多语句、LIMIT 与注释绕过。
 *
 * @author BlueWhale
 */
class DataQueryGuardTest {

    /** 测试白名单视图 */
    private static final List<String> VIEWS =
            List.of("v_ai_agent_fact_summary", "v_ai_agent_usage_summary");

    @Test
    @DisplayName("合法 SELECT：追加 LIMIT 并返回视图")
    void shouldAppendLimit() {
        DataQueryGuard.GuardedQuery guarded = DataQueryGuard.check(
                "select fact_type, fact_count from v_ai_agent_fact_summary", VIEWS, 200);

        assertThat(guarded.sql()).endsWith("LIMIT 200");
        assertThat(guarded.views()).containsExactly("v_ai_agent_fact_summary");
    }

    @Test
    @DisplayName("已有 LIMIT 超过上限：裁剪到上限")
    void shouldTrimLimit() {
        DataQueryGuard.GuardedQuery guarded = DataQueryGuard.check(
                "select * from v_ai_agent_usage_summary limit 500", VIEWS, 200);

        assertThat(guarded.sql()).endsWith("LIMIT 200");
    }

    @Test
    @DisplayName("白名单外视图：拒绝")
    void shouldRejectUnknownView() {
        assertThatThrownBy(() -> DataQueryGuard.check("select * from agent_message", VIEWS, 200))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未授权的视图");
    }

    @Test
    @DisplayName("写语句与多语句：拒绝")
    void shouldRejectWriteAndMultipleStatements() {
        assertThatThrownBy(() -> DataQueryGuard.check("delete from v_ai_agent_fact_summary", VIEWS, 200))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅允许 SELECT");
        assertThatThrownBy(() -> DataQueryGuard.check(
                "select * from v_ai_agent_fact_summary; drop table agent_message", VIEWS, 200))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("单条 SQL");
    }

    @Test
    @DisplayName("注释与子查询：不能绕过白名单")
    void shouldRejectHiddenTable() {
        assertThatThrownBy(() -> DataQueryGuard.check(
                "select * from (select * from agent_message) t", VIEWS, 200))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未授权的视图");

        DataQueryGuard.GuardedQuery guarded = DataQueryGuard.check(
                "select /* note */ * from v_ai_agent_fact_summary -- tail", VIEWS, 200);
        assertThat(guarded.sql()).startsWith("select");
        assertThat(guarded.sql()).doesNotContain("note", "tail");
    }

    @Test
    @DisplayName("系统库与时延函数：拒绝")
    void shouldRejectSystemSchema() {
        assertThatThrownBy(() -> DataQueryGuard.check(
                "select * from information_schema.tables", VIEWS, 200))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> DataQueryGuard.check(
                "select sleep(1) from v_ai_agent_fact_summary", VIEWS, 200))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非法函数");
    }

    @Test
    @DisplayName("WITH 与非末尾 LIMIT：拒绝")
    void shouldRejectWithAndInlineLimit() {
        assertThatThrownBy(() -> DataQueryGuard.check(
                "with t as (select 1) select * from t", VIEWS, 200))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅允许 SELECT");
        assertThatThrownBy(() -> DataQueryGuard.check(
                "select * from v_ai_agent_fact_summary limit 10 union select * from v_ai_agent_usage_summary",
                VIEWS, 200))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("LIMIT");
    }
}
