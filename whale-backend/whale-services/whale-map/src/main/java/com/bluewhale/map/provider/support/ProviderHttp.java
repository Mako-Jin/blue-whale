package com.bluewhale.map.provider.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.map.common.MapErrorCode;
import com.bluewhale.map.config.MapProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 供应商 HTTP 调用的共用胶水。
 *
 * <p><b>异常信息不带 URL</b>：Key 是查询参数的一部分，把原始异常消息（含完整 URL）
 * 直接抛给前端或写进日志，等于把 Key 打印出来。这里统一只保留供应商名、路径与异常类型。</p>
 *
 * @author BlueWhale
 */
public final class ProviderHttp {

    /** JSON 解析器（线程安全，可共享） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private ProviderHttp() {
    }

    /**
     * 按供应商配置构建 REST 客户端
     *
     * @param config 供应商配置
     * @return REST 客户端
     */
    public static RestClient build(MapProperties.ProviderConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getConnectTimeoutMillis());
        factory.setReadTimeout(config.getReadTimeoutMillis());
        return RestClient.builder()
                .baseUrl(trimTrailingSlash(config.getBaseUrl()))
                .requestFactory(factory)
                .build();
    }

    /**
     * 发起 GET 请求并解析为 JSON
     *
     * @param client    REST 客户端
     * @param provider  供应商名（用于报错）
     * @param path      接口路径
     * @param params    查询参数（含 Key）
     * @return 响应 JSON
     */
    public static JsonNode get(RestClient client, String provider, String path,
                               MultiValueMap<String, String> params) {
        try {
            String body = client.get()
                    .uri(builder -> builder.path(path).queryParams(params).build())
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new BizException(MapErrorCode.PROVIDER_FAILED, "map.provider.http.empty-body",
                        provider + " 返回空响应", provider);
            }
            return MAPPER.readTree(body);
        } catch (BizException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BizException(MapErrorCode.PROVIDER_FAILED, "map.provider.http.call-failed",
                    provider + " 调用失败（" + ex.getClass().getSimpleName() + "），路径 " + path,
                    provider, ex.getClass().getSimpleName(), path);
        } catch (Exception ex) {
            throw new BizException(MapErrorCode.PROVIDER_FAILED, "map.provider.http.parse-failed",
                    provider + " 响应解析失败（" + ex.getClass().getSimpleName() + "）",
                    provider, ex.getClass().getSimpleName());
        }
    }

    /**
     * 去掉基址末尾的斜杠，避免与路径拼出双斜杠
     *
     * @param baseUrl 基址
     * @return 规整后的基址
     */
    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
