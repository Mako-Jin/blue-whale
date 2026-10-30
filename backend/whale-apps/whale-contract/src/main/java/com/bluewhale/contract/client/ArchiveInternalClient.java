package com.bluewhale.contract.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.contract.config.ContractArchiveProperties;
import com.bluewhale.contract.entity.CtContract;
import com.bluewhale.contract.entity.CtContractParty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * whale-archive 业务快照入档客户端。
 *
 * <p>调用 {@code POST /archive/internal/items}，把合同终态快照推送档案服务；
 * 档案服务按 {@code (CONTRACT, 合同ID)} 幂等，重复推送返回既有条目，
 * 因此归档失败后重试不会产生重复档案。归档条目ID回填到 {@code ct_contract.archive_item_id}。</p>
 *
 * @author BlueWhale
 */
@Component
public class ArchiveInternalClient {

    /** 内网入档路径 */
    private static final String INGEST_PATH = "/archive/internal/items";

    /** 来源类型：合同 */
    private static final String SOURCE_TYPE_CONTRACT = "CONTRACT";

    /** 定义编码 */
    private static final String DEFINITION_CODE_CONTRACT = "CONTRACT";

    /** 统一响应体状态码字段 */
    private static final String FIELD_CODE = "code";

    /** 统一响应体消息字段 */
    private static final String FIELD_MESSAGE = "message";

    /** 统一响应体数据字段 */
    private static final String FIELD_DATA = "data";

    /** 归档条目ID字段 */
    private static final String FIELD_ITEM_ID = "itemId";

    /** 成功状态码 */
    private static final int CODE_SUCCESS = 0;

    /** 默认租户ID */
    private static final long DEFAULT_TENANT_ID = 1L;

    /** 快照 JSON 序列化器（线程安全） */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;

    private final ContractArchiveProperties properties;

    /**
     * 按归档配置构建客户端。
     *
     * @param properties 归档配置
     */
    public ArchiveInternalClient(ContractArchiveProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 推送合同快照入档。
     *
     * @param contract 合同实体（需已带终态时间与正文文件信息）
     * @param parties 合同相对方
     * @return 档案条目ID
     */
    public Long ingest(CtContract contract, List<CtContractParty> parties) {
        Map<String, Object> body = buildPayload(contract, parties);
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(INGEST_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebHeaders.TENANT_ID, tenantHeader())
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "合同快照入档失败: " + ex.getMessage());
        }
        if (response == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "合同快照入档失败: 空响应");
        }
        Object code = response.get(FIELD_CODE);
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "合同快照入档失败: " + response.get(FIELD_MESSAGE));
        }
        Object data = response.get(FIELD_DATA);
        Object itemId = data instanceof Map<?, ?> map ? map.get(FIELD_ITEM_ID) : null;
        Long parsed = parseId(itemId);
        if (parsed == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "合同快照入档失败: 响应缺少档案条目ID");
        }
        return parsed;
    }

    /**
     * 构造入档请求体；快照内容为合同核心字段与相对方清单的 JSON 文本。
     *
     * @param contract 合同实体
     * @param parties 合同相对方
     * @return 请求体
     */
    Map<String, Object> buildPayload(CtContract contract, List<CtContractParty> parties) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", SOURCE_TYPE_CONTRACT);
        payload.put("sourceId", String.valueOf(contract.getId()));
        payload.put("title", contract.getTitle());
        payload.put("summary", contract.getRemark());
        payload.put("keywords", contract.getCode() + "," + contract.getCategory());
        payload.put("securityLevel", properties.getSecurityLevel());
        payload.put("definitionCode", DEFINITION_CODE_CONTRACT);
        payload.put("businessKey", contract.getCode());
        payload.put("initiator", contract.getOwnerId() == null ? null : String.valueOf(contract.getOwnerId()));
        payload.put("fileName", "CONTRACT-" + contract.getCode() + ".json");
        payload.put("snapshotJson", snapshotJson(contract, parties));
        if (contract.getFileId() != null) {
            payload.put("originalFileId", contract.getFileId());
            payload.put("originalFileName", contract.getCode() + ".pdf");
            payload.put("originalContentType", "application/pdf");
        }
        return payload;
    }

    /**
     * 生成不可变快照文本。
     *
     * @param contract 合同实体
     * @param parties 合同相对方
     * @return 快照 JSON
     */
    private String snapshotJson(CtContract contract, List<CtContractParty> parties) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contractId", String.valueOf(contract.getId()));
        snapshot.put("code", contract.getCode());
        snapshot.put("title", contract.getTitle());
        snapshot.put("category", contract.getCategory());
        snapshot.put("amount", contract.getAmount());
        snapshot.put("currency", contract.getCurrency());
        snapshot.put("status", contract.getStatus());
        snapshot.put("currentVersion", contract.getCurrentVersion());
        snapshot.put("fileId", contract.getFileId() == null ? null : String.valueOf(contract.getFileId()));
        snapshot.put("effectiveDate", contract.getEffectiveDate() == null
                ? null : contract.getEffectiveDate().toString());
        snapshot.put("expireDate", contract.getExpireDate() == null ? null : contract.getExpireDate().toString());
        snapshot.put("bizType", contract.getBizType());
        snapshot.put("bizId", contract.getBizId());
        snapshot.put("parties", partySnapshots(parties));
        try {
            return JSON.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "合同快照序列化失败: " + ex.getMessage());
        }
    }

    /**
     * 生成相对方快照列表。
     *
     * @param parties 合同相对方
     * @return 相对方快照
     */
    private List<Map<String, Object>> partySnapshots(List<CtContractParty> parties) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (parties == null) {
            return result;
        }
        for (CtContractParty party : parties) {
            if (party == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("partyType", party.getPartyType());
            row.put("name", party.getName());
            row.put("creditCode", party.getCreditCode());
            row.put("contact", party.getContact());
            row.put("signerName", party.getSignerName());
            result.add(row);
        }
        return result;
    }

    /**
     * 取当前租户请求头；缺失租户上下文时兜底默认租户。
     *
     * @return 租户ID字符串
     */
    private String tenantHeader() {
        Long tenantId = TenantContext.get();
        return String.valueOf(tenantId == null ? DEFAULT_TENANT_ID : tenantId);
    }

    /**
     * 解析档案条目ID：平台统一响应体以字符串承载长整型。
     *
     * @param value 原始ID值
     * @return 条目ID；无法解析返回 {@code null}
     */
    private Long parseId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
