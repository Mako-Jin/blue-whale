package com.bluewhale.sts.app.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 日志服务的端到端测试 —— 一条请求真实穿过 Controller → Service → Mapper → H2。
 *
 * <p>放在**应用层**而不是日志模块内：这里验证的是"整条链路装配正确"，
 * 包括 Mapper 扫描、MyBatis-Plus 拦截器、建表脚本、统一响应包装是否同时生效。
 * 这些正是单元测试覆盖不到、又最容易在真实部署时才炸掉的部分。
 *
 * <p>{@link Transactional} 让每个用例结束后自动回滚，用例之间互不干扰，
 * 也不必在断言里处理历史数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClientLogApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("上报 → 按链路查询：合法条目落库，非法条目被单独计数")
    void shouldReportAndQueryByTraceId() throws Exception {
        String traceId = "trace-" + UUID.randomUUID();

        String body = """
                {
                  "app": "it-test",
                  "release": "0.1.0",
                  "sessionId": "session-1",
                  "logs": [
                    {"level": "info",  "namespace": "it › step", "message": "第一步", "timestamp": 1, "traceId": "%s"},
                    {"level": "error", "namespace": "it › step", "message": "第二步", "timestamp": 2, "traceId": "%s",
                     "data": [1, "two"], "context": {"userId": "u-1"}},
                    {"level": "verbose", "namespace": "it › step", "message": "级别非法", "timestamp": 3, "traceId": "%s"}
                  ]
                }
                """.formatted(traceId, traceId, traceId);

        mockMvc.perform(post("/api/logs/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accepted").value(2))
                .andExpect(jsonPath("$.data.rejected").value(1));

        mockMvc.perform(get("/api/logs/client/trace/{traceId}", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 升序返回：链路的阅读顺序就是发生顺序
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].message").value("第一步"))
                .andExpect(jsonPath("$.data[1].level").value("error"))
                // 结构化字段进得去、出得来
                .andExpect(jsonPath("$.data[1].data[1]").value("two"))
                .andExpect(jsonPath("$.data[1].context.userId").value("u-1"))
                // 信封级元数据回填到了条目上
                .andExpect(jsonPath("$.data[0].sessionId").value("session-1"))
                .andExpect(jsonPath("$.data[0].release").value("0.1.0"));
    }

    @Test
    @DisplayName("分页查询：按级别过滤，并由服务端算好总页数")
    void shouldPageAndFilter() throws Exception {
        String app = "it-page-" + UUID.randomUUID();
        String namespace = app + " › ns";
        report(app, namespace, "warn", 3);
        report(app, namespace, "error", 2);

        mockMvc.perform(get("/api/logs/client")
                        .param("namespace", namespace)
                        .param("level", "warn")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                // 3 条按每页 2 条 = 2 页，向上取整由服务端负责
                .andExpect(jsonPath("$.data.pages").value(2));
    }

    @Test
    @DisplayName("每页条数被钳制在上限内，避免一次请求拖垮服务")
    void shouldClampPageSize() throws Exception {
        mockMvc.perform(get("/api/logs/client").param("size", "100000"))
                .andExpect(status().isOk())
                // PageQuery.MAX_SIZE
                .andExpect(jsonPath("$.data.size").value(200));
    }

    @Test
    @DisplayName("聚合统计补齐没有数据的级别，便于直接绘图")
    void shouldReturnAllLevelsIncludingEmptyOnes() throws Exception {
        String app = "it-stats-" + UUID.randomUUID();
        String namespace = app + " › ns";
        report(app, namespace, "info", 2);
        report(app, namespace, "error", 1);

        mockMvc.perform(get("/api/logs/client/stats").param("app", app))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.levels.length()").value(7))
                .andExpect(jsonPath("$.data.startTime").isNumber())
                .andExpect(jsonPath("$.data.endTime").isNumber());
    }

    @Test
    @DisplayName("信封级错误整批拒绝：logs 为空")
    void shouldRejectEmptyBatch() throws Exception {
        String body = """
                {"app": "it-test", "logs": []}
                """;

        mockMvc.perform(post("/api/logs/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @DisplayName("信封级错误整批拒绝：app 缺失")
    void shouldRejectMissingApp() throws Exception {
        String body = """
                {"logs": [{"level": "info", "message": "x"}]}
                """;

        mockMvc.perform(post("/api/logs/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                // 回传字段级原因，联调时不必靠猜
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("app")));
    }

    @Test
    @DisplayName("查询不存在的日志返回 40400，而不是 50000")
    void shouldReturnNotFoundCode() throws Exception {
        mockMvc.perform(get("/api/logs/client/{id}", 999_999_999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("参数类型错误回 40000 而非 50000：责任在调用方，不该算作服务端故障")
    void shouldTreatTypeMismatchAsClientError() throws Exception {
        mockMvc.perform(get("/api/logs/client").param("page", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @DisplayName("响应体不泄漏 success 字段：业务结果只由 code 表达")
    void shouldNotExposeDerivedSuccessField() throws Exception {
        mockMvc.perform(get("/api/logs/client"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    private void report(String app, String namespace, String level, int count) throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("""
                    {"level": "%s", "namespace": "%s", "message": "第 %d 条", "timestamp": %d}
                    """.formatted(level, namespace, i, i));
        }
        String body = "{\"app\": \"" + app + "\", \"logs\": [" + items + "]}";

        mockMvc.perform(post("/api/logs/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

}
