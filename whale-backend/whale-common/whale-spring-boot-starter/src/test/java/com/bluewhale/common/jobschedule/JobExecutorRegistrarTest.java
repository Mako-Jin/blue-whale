package com.bluewhale.common.jobschedule;

import com.bluewhale.common.web.WebHeaders;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 执行器注册器的租户透传测试。
 *
 * <p>内网接口靠请求头 {@code X-Tenant-Id} 建立租户上下文，注册器必须把配置的执行器租户
 * 带到注册、心跳、注销三个请求上，否则调度中心会把非默认租户的执行器归到默认租户。</p>
 *
 * @author BlueWhale
 */
class JobExecutorRegistrarTest {

    /** 执行器所属租户：使用非默认值，确保断言不会被默认租户蒙混通过 */
    private static final long TENANT_ID = 2L;

    private static HttpServer server;

    private static final List<CapturedRequest> REQUESTS = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void setUpClass() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/job/internal/executor", exchange -> {
            REQUESTS.add(new CapturedRequest(exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst(WebHeaders.TENANT_ID)));
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"code\":0,\"data\":true,\"message\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void tearDownClass() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    @DisplayName("注册、心跳、注销请求均携带 X-Tenant-Id")
    void shouldPropagateTenantHeader() throws Exception {
        REQUESTS.clear();
        JobScheduleProperties properties = new JobScheduleProperties();
        properties.getExecutor().setServerUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getExecutor().setTenantId(TENANT_ID);
        properties.getExecutor().setAppName("bw-crm");
        properties.getExecutor().setAddress("10.0.0.9:9007");
        JobExecutorRegistrar registrar = new JobExecutorRegistrar(properties, new RemoteJobRegistry(),
                new ObjectMapper());

        registrar.onApplicationEvent(null);
        invokeBeat(registrar);
        registrar.destroy();

        assertEquals(3, REQUESTS.size());
        assertEquals("POST", REQUESTS.get(0).method());
        assertEquals("/job/internal/executor/register", REQUESTS.get(0).path());
        assertEquals("PUT", REQUESTS.get(1).method());
        assertEquals("/job/internal/executor/beat", REQUESTS.get(1).path());
        assertEquals("DELETE", REQUESTS.get(2).method());
        for (CapturedRequest request : REQUESTS) {
            assertNotNull(request.tenantHeader());
            assertEquals(String.valueOf(TENANT_ID), request.tenantHeader());
        }
        assertTrue(REQUESTS.get(0).path().startsWith("/job/internal/executor"));
    }

    /**
     * 反射触发私有心跳方法，避免测试等待 5 秒的首次心跳延迟
     *
     * @param registrar 注册器
     * @throws Exception 反射调用失败
     */
    private void invokeBeat(JobExecutorRegistrar registrar) throws Exception {
        Method beat = JobExecutorRegistrar.class.getDeclaredMethod("beat");
        beat.setAccessible(true);
        beat.invoke(registrar);
    }

    /**
     * 捕获的请求快照
     *
     * @param method HTTP 方法
     * @param path 请求路径
     * @param tenantHeader X-Tenant-Id 头
     */
    private record CapturedRequest(String method, String path, String tenantHeader) {
    }
}
