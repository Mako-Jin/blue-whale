package com.bluewhale.oa.document.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.document.domain.gateway.ArchiveGateway;
import com.bluewhale.oa.document.domain.gateway.ArchivePushCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 档案服务网关实现。
 *
 * <p>调用 whale-archive 内网入档接口 {@code POST /archive/internal/items}；
 * 该接口按 {@code (sourceType, sourceId)} 幂等，重复推送返回既有条目。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArchiveGatewayImpl implements ArchiveGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 档案服务名 */
    private static final String SERVICE_NAME = "whale-archive";

    private final DiscoveryClient discoveryClient;

    @Override
    public String archive(ArchivePushCommand command) {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceType", command.getSourceType());
        body.put("sourceId", command.getSourceId());
        body.put("title", command.getTitle());
        body.put("summary", command.getSummary());
        body.put("keywords", command.getKeywords());
        body.put("securityLevel", command.getSecurityLevel());
        body.put("definitionCode", command.getDefinitionCode());
        body.put("businessKey", command.getBusinessKey());
        body.put("initiator", command.getInitiator());
        body.put("finishedAt", command.getFinishedAt() == null ? null : command.getFinishedAt().toString());
        body.put("snapshotJson", command.getSnapshotJson());
        body.put("fileName", command.getFileName());
        body.put("originalFileId", command.getOriginalFileId());
        body.put("originalFileName", command.getOriginalFileName());
        body.put("originalContentType", command.getOriginalContentType());
        body.put("originalFileSize", command.getOriginalFileSize());

        Map<?, ?> response;
        try {
            response = InternalTokenSupport.restClient()
                    .post()
                    .uri(baseUrl() + "/archive/internal/items")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException | ResourceAccessException | IllegalStateException ex) {
            log.warn("公文归档推送失败: sourceId={}, error={}", command.getSourceId(), ex.getMessage());
            throw OaErrorCode.exception(OaErrorCode.DOC_ARCHIVE_UNAVAILABLE);
        }
        if (response == null) {
            throw OaErrorCode.exception(OaErrorCode.DOC_ARCHIVE_UNAVAILABLE);
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw OaErrorCode.exception(OaErrorCode.DOC_ARCHIVE_UNAVAILABLE);
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> row) || row.get("itemId") == null) {
            throw OaErrorCode.exception(OaErrorCode.DOC_ARCHIVE_UNAVAILABLE);
        }
        return String.valueOf(row.get("itemId"));
    }

    /**
     * 解析档案服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw new IllegalStateException("档案服务不可用：" + SERVICE_NAME);
        }
        return instances.getFirst().getUri().toString();
    }
}
