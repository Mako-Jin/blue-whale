package com.bluewhale.archive.support;

import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 内网服务调用支撑：RestClient 构建与统一响应体解析。
 *
 * <p>平台统一响应体为 {@code {code, message, data}}，仅 {@code code=0} 视为成功；
 * 网络异常与非成功响应统一转换为 {@link BizException}，不向调用方泄露原始异常。</p>
 *
 * @author BlueWhale
 */
public final class RemoteHttpSupport {

    /** 统一响应体状态码字段 */
    private static final String FIELD_CODE = "code";

    /** 统一响应体消息字段 */
    private static final String FIELD_MESSAGE = "message";

    /** 统一响应体数据字段 */
    private static final String FIELD_DATA = "data";

    /** 工具类不允许实例化 */
    private RemoteHttpSupport() {
    }

    /**
     * 按集成配置构建 RestClient（带连接/读取超时）。
     *
     * @param baseUrl 服务基址
     * @param integration 集成配置（提供超时）
     * @return RestClient 实例
     */
    public static RestClient build(String baseUrl, ArchiveProperties.Integration integration) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(integration.getConnectTimeoutMillis());
        factory.setReadTimeout(integration.getReadTimeoutMillis());
        return InternalTokenSupport.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * 校验统一响应体并取出 {@code data}。
     *
     * @param response 响应体
     * @param action 动作描述（用于错误信息）
     * @return {@code data} 字段值
     * @throws BizException 响应为空或 {@code code != 0} 时抛出
     */
    public static Object requireData(Map<String, Object> response, String action) {
        if (response == null) {
            throw failure(action, "空响应");
        }
        Object code = response.get(FIELD_CODE);
        if (!(code instanceof Number number) || number.intValue() != ErrorCode.SUCCESS.getCode()) {
            throw failure(action, String.valueOf(response.get(FIELD_MESSAGE)));
        }
        return response.get(FIELD_DATA);
    }

    /**
     * 将 {@code data} 转换为目标类型。
     *
     * @param mapper JSON 映射器
     * @param data {@code data} 字段值
     * @param type 目标类型
     * @param <T> 目标类型
     * @param action 动作描述（用于错误信息）
     * @return 目标对象；{@code data} 为空时返回 {@code null}
     * @throws BizException 转换失败时抛出
     */
    public static <T> T decode(ObjectMapper mapper, Object data, Class<T> type, String action) {
        if (data == null) {
            return null;
        }
        try {
            return mapper.convertValue(data, type);
        } catch (IllegalArgumentException e) {
            throw failure(action, "响应结构不匹配");
        }
    }

    /**
     * 执行一次 HTTP 调用并把网络异常统一转换为受控业务异常。
     *
     * @param supplier HTTP 调用
     * @param action 动作描述（用于错误信息）
     * @return 响应体
     * @throws BizException 网络异常时抛出
     */
    public static Map<String, Object> call(Supplier<Map<String, Object>> supplier, String action) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw failure(action, e.getMessage());
        }
    }

    /**
     * 构造受控失败异常。
     *
     * @param action 动作描述
     * @param message 失败原因
     * @return 业务异常
     */
    public static BizException failure(String action, String message) {
        return new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "biz.archive.support.failed", "{0}失败: {1}", action, message);
    }
}
