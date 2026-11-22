package com.bluewhale.contract.esign.provider;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.contract.config.ContractEsignProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 三方电子签开放平台 HTTP 客户端。
 *
 * <p>实现 BlueWhale 电子签开放平台协议 v1：创建任务 {@code POST createPath}、查询
 * {@code GET queryPath}、下载 {@code GET downloadPath}，统一使用
 * {@link EsignRequestSigner} 做 HMAC-SHA256 请求签名。</p>
 *
 * <p>请求与响应均为 JSON 信封 {@code {code, message, data}}；创建失败、状态缺失、
 * 下载为空等均转为 {@link BizException}，不再生成假任务号或静默返回 SIGNING。</p>
 *
 * @author BlueWhale
 */
public class EsignOpenApiClient {

    /** 创建任务 HTTP 方法 */
    private static final String METHOD_POST = "POST";

    /** 查询/下载 HTTP 方法 */
    private static final String METHOD_GET = "GET";

    /** 路径变量占位 */
    private static final String PATH_VARIABLE = "{providerTaskId}";

    /** 成功响应码 */
    private static final int CODE_SUCCESS = 0;

    /** 默认连接超时（毫秒） */
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;

    /** 默认读取超时（毫秒） */
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;

    /** JSON 序列化器 */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 渠道名 */
    private final String providerName;

    /** 渠道配置 */
    private final ContractEsignProperties.ProviderConfig config;

    /** HTTP 客户端 */
    private final RestClient restClient;

    /**
     * 构造客户端。
     *
     * @param providerName 渠道名
     * @param config       渠道配置，可为空（本地渠道不会发起调用）
     */
    public EsignOpenApiClient(String providerName, ContractEsignProperties.ProviderConfig config) {
        this(providerName, config, buildRestClient(config));
    }

    /**
     * 构造客户端，允许注入已配置的 {@link RestClient} 以便测试。
     *
     * @param providerName 渠道名
     * @param config       渠道配置
     * @param restClient   HTTP 客户端
     */
    EsignOpenApiClient(String providerName, ContractEsignProperties.ProviderConfig config,
                       RestClient restClient) {
        this.providerName = providerName;
        this.config = config;
        this.restClient = restClient;
    }

    /**
     * 按渠道配置构建带超时的 HTTP 客户端。
     *
     * @param config 渠道配置
     * @return HTTP 客户端
     */
    private static RestClient buildRestClient(ContractEsignProperties.ProviderConfig config) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout(config == null ? 0 : config.getConnectTimeoutMillis(),
                DEFAULT_CONNECT_TIMEOUT_MILLIS));
        requestFactory.setReadTimeout(timeout(config == null ? 0 : config.getReadTimeoutMillis(),
                DEFAULT_READ_TIMEOUT_MILLIS));
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (config != null && StringUtils.hasText(config.getBaseUrl())) {
            builder = builder.baseUrl(trimTrailingSlash(config.getBaseUrl().trim()));
        }
        return builder.build();
    }

    /**
     * 创建三方签署任务。
     *
     * @param command 创建任务上下文
     * @return 三方任务号与签署链接
     */
    public EsignTaskResult createTask(EsignTaskCommand command) {
        requireConfigured();
        String path = normalizePath(config.getCreatePath(), "create-path");
        String body = toJson(buildCreatePayload(command));
        Map<String, Object> response = post(path, body);
        Map<String, Object> data = requireData(response);
        String providerTaskId = firstText(data.get("taskId"), response.get("taskId"));
        if (!StringUtils.hasText(providerTaskId)) {
            throw callFailed("创建任务响应缺少 taskId");
        }
        String signUrl = firstText(data.get("signUrl"), response.get("signUrl"));
        if (!StringUtils.hasText(signUrl)) {
            signUrl = resolveSignUrl(providerTaskId, command);
        }
        return new EsignTaskResult(providerTaskId, signUrl);
    }

    /**
     * 查询三方签署任务状态。
     *
     * @param providerTaskId 三方任务号
     * @return 三方任务状态
     */
    public EsignTaskStatus queryTask(String providerTaskId) {
        requireConfigured();
        String path = resolveTaskPath(config.getQueryPath(), "query-path", providerTaskId);
        Map<String, Object> response = get(path);
        Map<String, Object> data = requireData(response);
        String status = firstText(data.get("status"), response.get("status"));
        if (!StringUtils.hasText(status)) {
            throw callFailed("查询任务响应缺少 status");
        }
        return new EsignTaskStatus(status.trim().toUpperCase(Locale.ROOT),
                firstText(data.get("message"), response.get("message")));
    }

    /**
     * 下载三方签章件。
     *
     * <p>兼容二进制 PDF 与 JSON {@code data.fileBase64} 两种响应。</p>
     *
     * @param providerTaskId 三方任务号
     * @return 签章件字节
     */
    public byte[] downloadSignedFile(String providerTaskId) {
        requireConfigured();
        String path = resolveTaskPath(config.getDownloadPath(), "download-path", providerTaskId);
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri(path)
                    .headers(headers -> applyAuthHeaders(headers, METHOD_GET, path, ""))
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] payload = response.getBody();
            if (payload == null || payload.length == 0) {
                throw callFailed("下载签章件响应为空");
            }
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType != null && contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                Map<String, Object> data = requireData(parseObject(payload));
                String fileBase64 = firstText(data.get("fileBase64"));
                if (!StringUtils.hasText(fileBase64)) {
                    throw callFailed("下载签章件响应缺少 data.fileBase64");
                }
                return Base64.getDecoder().decode(fileBase64);
            }
            return payload;
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw callFailed("下载签章件调用失败: " + ex.getMessage());
        }
    }

    /**
     * 组装创建任务请求体。
     *
     * @param command 创建任务上下文
     * @return 请求体
     */
    private Map<String, Object> buildCreatePayload(EsignTaskCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", providerName);
        payload.put("signTaskId", command.signTaskId() == null ? null : String.valueOf(command.signTaskId()));
        payload.put("contractId", command.contractId() == null ? null : String.valueOf(command.contractId()));
        payload.put("contractCode", command.contractCode());
        payload.put("contractTitle", command.contractTitle());
        payload.put("signerName", command.signerName());
        payload.put("signerParty", command.signerParty());
        if (StringUtils.hasText(config.getNotifyUrl())) {
            payload.put("notifyUrl", config.getNotifyUrl());
        }
        if (command.contractFile() != null && command.contractFile().length > 0) {
            payload.put("contractFileName", command.contractFileName());
            payload.put("contractFileBase64", Base64.getEncoder().encodeToString(command.contractFile()));
        }
        return payload;
    }

    /**
     * 发送 POST 请求并解析 JSON 信封。
     *
     * @param path 请求路径
     * @param body 已序列化请求体
     * @return 响应信封
     */
    private Map<String, Object> post(String path, String body) {
        try {
            return restClient.post()
                    .uri(path)
                    .headers(headers -> applyAuthHeaders(headers, METHOD_POST, path, body))
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw callFailed("创建任务调用失败: " + ex.getMessage());
        }
    }

    /**
     * 发送 GET 请求并解析 JSON 信封。
     *
     * @param path 请求路径
     * @return 响应信封
     */
    private Map<String, Object> get(String path) {
        try {
            return restClient.get()
                    .uri(path)
                    .headers(headers -> applyAuthHeaders(headers, METHOD_GET, path, ""))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw callFailed("查询任务调用失败: " + ex.getMessage());
        }
    }

    /**
     * 写入签名相关请求头。
     *
     * @param headers HTTP 头
     * @param method  HTTP 方法
     * @param path    请求路径
     * @param body    请求体
     */
    private void applyAuthHeaders(HttpHeaders headers, String method, String path, String body) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String signature = EsignRequestSigner.sign(config.getAppSecret(), method, path, timestamp, nonce, body);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(EsignRequestSigner.HEADER_APP_ID, config.getAppId());
        headers.set(EsignRequestSigner.HEADER_TIMESTAMP, timestamp);
        headers.set(EsignRequestSigner.HEADER_NONCE, nonce);
        headers.set(EsignRequestSigner.HEADER_SIGNATURE, signature);
    }

    /**
     * 校验响应信封并取出 data。
     *
     * @param response 响应信封
     * @return data 对象
     */
    private Map<String, Object> requireData(Map<String, Object> response) {
        if (response == null) {
            throw callFailed("响应为空");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw callFailed("响应码非成功: code=" + code + ", message=" + firstText(response.get("message")));
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> raw)) {
            throw callFailed("响应缺少 data 对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * 校验必填配置。
     */
    private void requireConfigured() {
        if (config == null) {
            throw callFailed("未配置 providers." + providerName);
        }
        if (!StringUtils.hasText(config.getBaseUrl())
                || !StringUtils.hasText(config.getAppId())
                || !StringUtils.hasText(config.getAppSecret())) {
            throw callFailed("缺少 base-url / app-id / app-secret 配置");
        }
    }

    /**
     * 规范化接口路径。
     *
     * @param path  配置路径
     * @param field 配置字段名
     * @return 以 / 开头的路径
     */
    private String normalizePath(String path, String field) {
        if (!StringUtils.hasText(path)) {
            throw callFailed("缺少 " + field + " 配置");
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    /**
     * 替换任务号路径变量。
     *
     * @param path           配置路径
     * @param field          配置字段名
     * @param providerTaskId 三方任务号
     * @return 已替换的路径
     */
    private String resolveTaskPath(String path, String field, String providerTaskId) {
        String normalized = normalizePath(path, field);
        if (!normalized.contains(PATH_VARIABLE)) {
            throw callFailed(field + " 必须包含 " + PATH_VARIABLE + " 占位");
        }
        String encoded = UriUtils.encodePathSegment(providerTaskId, StandardCharsets.UTF_8);
        return normalized.replace(PATH_VARIABLE, encoded);
    }

    /**
     * 使用配置模板兜底生成签署链接。
     *
     * @param providerTaskId 三方任务号
     * @param command        创建任务上下文
     * @return 签署链接；未配置模板时返回 {@code null}
     */
    private String resolveSignUrl(String providerTaskId, EsignTaskCommand command) {
        if (!StringUtils.hasText(config.getSignUrlTemplate())) {
            return null;
        }
        return config.getSignUrlTemplate()
                .replace(PATH_VARIABLE, providerTaskId)
                .replace("{signTaskId}", String.valueOf(command.signTaskId()));
    }

    /**
     * 取第一个非空字符串。
     *
     * @param values 候选值
     * @return 第一个非空值的字符串形式；均空时返回 {@code null}
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    /**
     * 序列化请求体。
     *
     * @param payload 请求体
     * @return JSON 字符串
     */
    private String toJson(Map<String, Object> payload) {
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw callFailed("请求体序列化失败");
        }
    }

    /**
     * 解析 JSON 字节。
     *
     * @param payload JSON 字节
     * @return 反序列化对象
     */
    private Map<String, Object> parseObject(byte[] payload) {
        try {
            return JSON.readValue(payload, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException ex) {
            throw callFailed("响应 JSON 解析失败");
        }
    }

    /**
     * 构造统一的调用失败异常。
     *
     * @param detail 失败详情
     * @return 业务异常
     */
    private BizException callFailed(String detail) {
        return new BizException(ErrorCode.SYSTEM_ERROR, "biz.contract.esign.e-sign-channel", "电子签渠道[{0}] {1}", providerName, detail);
    }

    /**
     * 去除地址尾部斜杠。
     *
     * @param value 地址
     * @return 去除尾部斜杠后的地址
     */
    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 超时兜底。
     *
     * @param configured 配置值
     * @param fallback   兜底值
     * @return 正整数超时
     */
    private static int timeout(int configured, int fallback) {
        return configured > 0 ? configured : fallback;
    }
}
