package com.bluewhale.contract.esign.provider;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.contract.config.ContractEsignProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 三方电子签开放平台 HTTP 客户端测试。
 *
 * @author BlueWhale
 */
class EsignOpenApiClientTest {

    /** 渠道配置 */
    private ContractEsignProperties.ProviderConfig config;

    /** Mock HTTP 服务端 */
    private MockRestServiceServer server;

    /** 被测客户端 */
    private EsignOpenApiClient client;

    /**
     * 初始化渠道配置与 Mock 服务端。
     */
    @BeforeEach
    void setUp() {
        config = new ContractEsignProperties.ProviderConfig();
        config.setBaseUrl("http://esign.test/");
        config.setAppId("app-1");
        config.setAppSecret("secret-1");
        config.setNotifyUrl("http://127.0.0.1:9006/contract/sign/callback/esignbao");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EsignOpenApiClient("esignbao", config,
                builder.baseUrl("http://esign.test").build());
    }

    /**
     * 创建任务应发送签名请求并解析三方任务号与签署链接。
     */
    @Test
    void createTaskCallsOpenApiAndParsesResult() {
        server.expect(requestTo("http://esign.test/v1/sign-tasks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    assertEquals("app-1", request.getHeaders().getFirst(EsignRequestSigner.HEADER_APP_ID));
                    String timestamp = request.getHeaders().getFirst(EsignRequestSigner.HEADER_TIMESTAMP);
                    String nonce = request.getHeaders().getFirst(EsignRequestSigner.HEADER_NONCE);
                    String signature = request.getHeaders().getFirst(EsignRequestSigner.HEADER_SIGNATURE);
                    String body = ((ByteArrayOutputStream) request.getBody())
                            .toString(StandardCharsets.UTF_8);
                    assertTrue(body.contains("\"contractCode\":\"HT-001\""));
                    assertTrue(body.contains("\"notifyUrl\":\"http://127.0.0.1:9006/contract/sign/callback/esignbao\""));
                    assertTrue(body.contains("\"contractFileBase64\":\""));
                    assertEquals(EsignRequestSigner.sign("secret-1", "POST", "/v1/sign-tasks",
                            timestamp, nonce, body), signature);
                })
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"taskId\":\"ES-100\","
                        + "\"signUrl\":\"https://sign.example.com/ES-100\"}}", MediaType.APPLICATION_JSON));

        EsignTaskCommand command = new EsignTaskCommand(1L, 2L, "HT-001", "采购合同", "张三", "A",
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8), "contract.pdf");
        EsignTaskResult result = client.createTask(command);

        assertEquals("ES-100", result.providerTaskId());
        assertEquals("https://sign.example.com/ES-100", result.signUrl());
        server.verify();
    }

    /**
     * 查询任务应解析三方状态，不再固定返回 SIGNING。
     */
    @Test
    void queryTaskParsesProviderStatus() {
        server.expect(requestTo("http://esign.test/v1/sign-tasks/ES-100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"status\":\"signed\",\"message\":\"ok\"}}",
                        MediaType.APPLICATION_JSON));

        EsignTaskStatus status = client.queryTask("ES-100");

        assertEquals("SIGNED", status.status());
        assertEquals("ok", status.message());
        server.verify();
    }

    /**
     * 下载签章件应支持二进制 PDF 响应。
     */
    @Test
    void downloadSignedFileReturnsBinaryPayload() {
        byte[] pdf = "%PDF-1.7 signed".getBytes(StandardCharsets.UTF_8);
        server.expect(requestTo("http://esign.test/v1/sign-tasks/ES-100/file"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF));

        assertArrayEquals(pdf, client.downloadSignedFile("ES-100"));
        server.verify();
    }

    /**
     * 下载签章件应兼容 JSON base64 响应。
     */
    @Test
    void downloadSignedFileSupportsJsonBase64() {
        byte[] pdf = "%PDF-1.7 signed".getBytes(StandardCharsets.UTF_8);
        String base64 = Base64.getEncoder().encodeToString(pdf);
        server.expect(requestTo("http://esign.test/v1/sign-tasks/ES-100/file"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"fileBase64\":\"" + base64 + "\"}}",
                        MediaType.APPLICATION_JSON));

        assertArrayEquals(pdf, client.downloadSignedFile("ES-100"));
        server.verify();
    }

    /**
     * 三方返回非成功码时应抛出业务异常，而不是返回假结果。
     */
    @Test
    void createTaskFailsWhenProviderReturnsError() {
        server.expect(requestTo("http://esign.test/v1/sign-tasks"))
                .andRespond(withSuccess("{\"code\":400,\"message\":\"signer invalid\"}",
                        MediaType.APPLICATION_JSON));

        EsignTaskCommand command = new EsignTaskCommand(1L, 2L, "HT-001", "采购合同", "张三", "A",
                null, null);

        assertThrows(BizException.class, () -> client.createTask(command));
        server.verify();
    }

    /**
     * 未配置接入参数时应在发起请求前失败。
     */
    @Test
    void createTaskFailsWhenProviderNotConfigured() {
        ContractEsignProperties.ProviderConfig empty = new ContractEsignProperties.ProviderConfig();
        EsignOpenApiClient unconfigured = new EsignOpenApiClient("esignbao", empty, RestClient.builder().build());

        assertThrows(BizException.class, () -> unconfigured.createTask(
                new EsignTaskCommand(1L, 2L, "HT-001", "采购合同", "张三", "A", null, null)));
    }
}
