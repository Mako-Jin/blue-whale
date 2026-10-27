package com.bluewhale.contract.esign.provider;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.contract.config.ContractEsignProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 电子签开放平台协议 v1 沙箱联调（真实 loopback HTTP）。
 *
 * <p>与仅 mock RestClient 的单元测试不同，本测试启动真实的 {@link HttpServer}，
 * 由服务端按协议重新计算 HMAC 请求签名并校验，覆盖创建 → 查询 → 下载全链路，
 * 可作为接入真实供应商沙箱前的本地协议一致性基线。</p>
 *
 * @author BlueWhale
 */
class EsignOpenApiSandboxTest {

    /** 测试 appId */
    private static final String APP_ID = "sandbox-app";

    /** 测试 appSecret */
    private static final String APP_SECRET = "sandbox-secret";

    /** 非 PDF 的下载字节（仅用于比对透传） */
    private static final byte[] SIGNED_FILE = "%PDF-1.7\nsandbox-signed-file".getBytes(StandardCharsets.ISO_8859_1);

    /**
     * 创建、查询、下载全链路在真实 HTTP 上完成，且服务端验签通过。
     *
     * @throws Exception 启动/调用异常
     */
    @Test
    void completesLifecycleOverRealHttp() throws Exception {
        try (SandboxServer sandbox = new SandboxServer()) {
            sandbox.start();
            EsignOpenApiClient client = new EsignOpenApiClient("esignbao", sandbox.config());

            EsignTaskResult created = client.createTask(new EsignTaskCommand(1001L, 2002L, "HT-2026-001",
                    "沙箱联调合同", "张三", "A", SIGNED_FILE, "contract.pdf"));

            assertEquals("vendor-task-1", created.providerTaskId());
            assertEquals("https://sandbox.example.com/sign/vendor-task-1", created.signUrl());
            assertTrue(sandbox.isCreateSignatureValid(), "服务端重算的创建请求签名不一致");
            assertTrue(sandbox.getLastCreateBody().contains("\"contractCode\":\"HT-2026-001\""),
                    sandbox.getLastCreateBody());
            assertTrue(sandbox.getLastCreateBody().contains("\"contractFileBase64\""));

            EsignTaskStatus status = client.queryTask(created.providerTaskId());
            assertEquals("SIGNED", status.status());
            assertTrue(sandbox.isQuerySignatureValid(), "服务端重算的查询请求签名不一致");

            byte[] downloaded = client.downloadSignedFile(created.providerTaskId());
            assertArrayEquals(SIGNED_FILE, downloaded);
            assertTrue(sandbox.isDownloadSignatureValid(), "服务端重算的下载请求签名不一致");
        }
    }

    /**
     * 供应商返回非成功业务码时转为 BizException，不生成假任务。
     *
     * @throws Exception 启动/调用异常
     */
    @Test
    void rejectsVendorBusinessError() throws Exception {
        try (SandboxServer sandbox = new SandboxServer()) {
            sandbox.setCreateResponse("{\"code\":40001,\"message\":\"app not authorized\"}");
            sandbox.start();
            EsignOpenApiClient client = new EsignOpenApiClient("esignbao", sandbox.config());

            BizException exception = assertThrows(BizException.class,
                    () -> client.createTask(new EsignTaskCommand(1L, 2L, "HT-1", "合同", "李四", "A",
                            new byte[]{1, 2, 3}, "contract.pdf")));

            assertTrue(exception.getMessage().contains("响应码非成功"), exception.getMessage());
            assertTrue(sandbox.isCreateSignatureValid(), "错误码场景也应先通过请求验签");
        }
    }

    /**
     * loopback 沙箱服务，按协议验签并模拟供应商响应。
     */
    private static final class SandboxServer implements AutoCloseable {

        /** HTTP 服务 */
        private final HttpServer server;

        /** 路径 → 是否验签通过 */
        private final Map<String, Boolean> signatureValid = new ConcurrentHashMap<>();

        /** 最近一次创建请求体 */
        private volatile String lastCreateBody = "";

        /** 创建任务响应体（可替换为失败场景） */
        private volatile String createResponse = "{\"code\":0,\"data\":{\"taskId\":\"vendor-task-1\","
                + "\"signUrl\":\"https://sandbox.example.com/sign/vendor-task-1\"}}";

        /**
         * 绑定随机端口。
         *
         * @throws IOException 绑定异常
         */
        private SandboxServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/tasks", this::handleCreate);
            server.createContext("/v1/tasks/", this::handleTask);
        }

        /**
         * 启动服务。
         */
        private void start() {
            server.start();
        }

        /**
         * 构造指向本服务的渠道配置。
         *
         * @return 渠道配置
         */
        private ContractEsignProperties.ProviderConfig config() {
            ContractEsignProperties.ProviderConfig config = new ContractEsignProperties.ProviderConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            config.setAppId(APP_ID);
            config.setAppSecret(APP_SECRET);
            config.setCreatePath("/v1/tasks");
            config.setQueryPath("/v1/tasks/{providerTaskId}");
            config.setDownloadPath("/v1/tasks/{providerTaskId}/file");
            config.setNotifyUrl("http://127.0.0.1/callback");
            return config;
        }

        /**
         * 处理创建任务。
         *
         * @param exchange 请求
         * @throws IOException 写出异常
         */
        private void handleCreate(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            lastCreateBody = body;
            boolean valid = verify(exchange, "POST", "/v1/tasks", body);
            signatureValid.put("create", valid);
            if (!valid) {
                respond(exchange, 401, "{\"code\":401,\"message\":\"bad signature\"}");
                return;
            }
            respond(exchange, 200, createResponse);
        }

        /**
         * 处理查询与下载（路径以 /v1/tasks/ 开头）。
         *
         * @param exchange 请求
         * @throws IOException 写出异常
         */
        private void handleTask(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String body = readBody(exchange);
            boolean valid = verify(exchange, exchange.getRequestMethod(), path, body);
            if (path.endsWith("/file")) {
                signatureValid.put("download", valid);
                if (!valid) {
                    respond(exchange, 401, "{\"code\":401,\"message\":\"bad signature\"}");
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                exchange.sendResponseHeaders(200, SIGNED_FILE.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(SIGNED_FILE);
                }
                return;
            }
            signatureValid.put("query", valid);
            if (!valid) {
                respond(exchange, 401, "{\"code\":401,\"message\":\"bad signature\"}");
                return;
            }
            respond(exchange, 200, "{\"code\":0,\"data\":{\"status\":\"SIGNED\"}}");
        }

        /**
         * 按协议重新计算并比对请求签名。
         *
         * @param exchange 请求
         * @param method   HTTP 方法
         * @param path     请求路径
         * @param body     请求体
         * @return 是否一致（含 appId）
         */
        private boolean verify(HttpExchange exchange, String method, String path, String body) {
            String appId = exchange.getRequestHeaders().getFirst(EsignRequestSigner.HEADER_APP_ID);
            String timestamp = exchange.getRequestHeaders().getFirst(EsignRequestSigner.HEADER_TIMESTAMP);
            String nonce = exchange.getRequestHeaders().getFirst(EsignRequestSigner.HEADER_NONCE);
            String signature = exchange.getRequestHeaders().getFirst(EsignRequestSigner.HEADER_SIGNATURE);
            if (appId == null || timestamp == null || nonce == null || signature == null) {
                return false;
            }
            String expected = EsignRequestSigner.sign(APP_SECRET, method, path, timestamp, nonce, body);
            return APP_ID.equals(appId) && expected.equals(signature);
        }

        /**
         * 读取请求体。
         *
         * @param exchange 请求
         * @return UTF-8 文本
         * @throws IOException 读取异常
         */
        private String readBody(HttpExchange exchange) throws IOException {
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }

        /**
         * 写出 JSON 响应。
         *
         * @param exchange 请求
         * @param status   HTTP 状态
         * @param body     响应体
         * @throws IOException 写出异常
         */
        private void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(payload);
            }
        }

        private boolean isCreateSignatureValid() {
            return Boolean.TRUE.equals(signatureValid.get("create"));
        }

        private boolean isQuerySignatureValid() {
            return Boolean.TRUE.equals(signatureValid.get("query"));
        }

        private boolean isDownloadSignatureValid() {
            return Boolean.TRUE.equals(signatureValid.get("download"));
        }

        private String getLastCreateBody() {
            return lastCreateBody;
        }

        private void setCreateResponse(String response) {
            this.createResponse = response;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
