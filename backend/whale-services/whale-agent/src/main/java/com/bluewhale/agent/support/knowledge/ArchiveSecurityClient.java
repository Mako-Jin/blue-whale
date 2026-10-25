package com.bluewhale.agent.support.knowledge;

import com.bluewhale.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 档案服务内网客户端：按文件ID查询所属档案条目的密级。
 *
 * <p>仅调用 {@code /archive/internal/**}，服务间直连、不经网关。密级查询失败或文件未被
 * 档案引用时返回 {@code null}，调用方按「拒绝入索引」处理（fail-closed），
 * 避免档案涉密内容进入可检索池。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class ArchiveSecurityClient {

    /** 密级查询路径模板 */
    private static final String SECURITY_PATH = "/archive/internal/files/{fileId}/security";

    /** 响应字段：状态码 */
    private static final String FIELD_CODE = "code";

    /** 响应字段：数据 */
    private static final String FIELD_DATA = "data";

    private final RestClient restClient;

    /**
     * 按采集配置构建客户端。
     *
     * @param properties Agent 配置
     */
    public ArchiveSecurityClient(AgentProperties properties) {
        AgentProperties.Knowledge knowledge = properties.getKnowledge();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(knowledge.getConnectTimeoutMillis());
        factory.setReadTimeout(knowledge.getReadTimeoutMillis());
        this.restClient = RestClient.builder()
                .baseUrl(knowledge.getArchiveUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 查询文件所属档案条目的密级。
     *
     * @param fileId 文件ID
     * @return 密级（1/2/3）；未归档或查询失败返回 {@code null}
     */
    public Integer securityLevel(Long fileId) {
        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(SECURITY_PATH, fileId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RuntimeException e) {
            log.warn("查询档案密级失败: fileId={}, error={}", fileId, e.getMessage());
            return null;
        }
        if (response == null || !(response.get(FIELD_CODE) instanceof Number number) || number.intValue() != 0) {
            log.warn("查询档案密级返回异常: fileId={}, response={}", fileId, response);
            return null;
        }
        Object data = response.get(FIELD_DATA);
        if (!(data instanceof Map<?, ?> map)) {
            return null;
        }
        Object level = map.get("securityLevel");
        if (level instanceof Number value) {
            return value.intValue();
        }
        return null;
    }
}
