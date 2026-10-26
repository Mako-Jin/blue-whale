package com.bluewhale.contract.esign;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.contract.config.ContractFileProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * whale-file 内网客户端：下载正文 / 上传签章件。
 *
 * <p>统一走 {@code /file/internal/**}，网关已对 {@code /file/internal/**} 做 403 屏蔽，
 * 因此服务间调用无需透传权限码，避免签署链路的权限放大。</p>
 *
 * @author BlueWhale
 */
@Component
public class FileClient {

    /** 内网下载路径 */
    private static final String CONTENT_PATH = "/file/internal/{id}/content";

    /** 内网上传路径 */
    private static final String UPLOAD_PATH = "/file/internal/upload";

    /** 统一响应体状态码字段 */
    private static final String FIELD_CODE = "code";

    /** 统一响应体数据字段 */
    private static final String FIELD_DATA = "data";

    /** 统一响应体主键字段 */
    private static final String FIELD_ID = "id";

    /** 成功状态码 */
    private static final int CODE_SUCCESS = 0;

    private final RestClient restClient;

    /**
     * 按文件服务配置构建客户端。
     *
     * @param properties 文件服务配置
     */
    public FileClient(ContractFileProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 下载文件内容。
     *
     * @param fileId 文件ID
     * @return 文件字节
     */
    public byte[] download(Long fileId) {
        if (fileId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件ID不能为空");
        }
        try {
            byte[] content = restClient.get()
                    .uri(CONTENT_PATH, fileId)
                    .retrieve()
                    .body(byte[].class);
            if (content == null || content.length == 0) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "文件内容为空: " + fileId);
            }
            return content;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "下载文件失败: " + ex.getMessage());
        }
    }

    /**
     * 上传文件（内网通道）。
     *
     * @param filename    文件名
     * @param contentType MIME 类型
     * @param content     文件内容
     * @return 新文件ID
     */
    public Long upload(String filename, String contentType, byte[] content) {
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", resource);
        try {
            Map<String, Object> response = restClient.post()
                    .uri(UPLOAD_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return requireFileId(response);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "上传签章件失败: " + ex.getMessage());
        }
    }

    /**
     * 校验上传响应并取出文件ID。
     *
     * @param response 统一响应体
     * @return 文件ID
     */
    private Long requireFileId(Map<String, Object> response) {
        if (response == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "上传签章件失败: 空响应");
        }
        Object code = response.get(FIELD_CODE);
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "上传签章件失败: " + response.get("message"));
        }
        Object data = response.get(FIELD_DATA);
        if (data instanceof Map<?, ?> map) {
            Object rawId = map.get(FIELD_ID);
            if (rawId instanceof Number id) {
                return id.longValue();
            }
            if (rawId instanceof String text && !text.isBlank()) {
                return Long.parseLong(text);
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "上传签章件失败: 响应缺少文件ID");
    }
}
