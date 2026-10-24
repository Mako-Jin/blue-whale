package com.bluewhale.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.entity.AgentOperation;
import com.bluewhale.agent.mapper.AgentOperationMapper;
import com.bluewhale.agent.support.ops.OperationInvoker;
import com.bluewhale.agent.support.ops.PlatformOperationCatalog;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.web.WebHeaders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 平台写操作确认服务：创建一次性令牌、确认执行、拒绝。
 *
 * <p>安全口径（规划 §6.4）：未确认不下发写请求；令牌绑定发起用户、5 分钟有效、
 * {@code PENDING → CONFIRMED} 通过条件更新保证只执行一次；执行结果与参数哈希落库，
 * 供审计按 {@code confirmation_id} 追溯。默认关闭（{@code whale.agent.ops.enabled=false}）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class PlatformOpService {

    /** 状态：待确认 */
    private static final String STATUS_PENDING = "PENDING";

    /** 状态：已确认（执行中） */
    private static final String STATUS_CONFIRMED = "CONFIRMED";

    /** 状态：已拒绝 */
    private static final String STATUS_REJECTED = "REJECTED";

    /** 状态：已过期 */
    private static final String STATUS_EXPIRED = "EXPIRED";

    /** 状态：执行成功 */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /** 状态：执行失败 */
    private static final String STATUS_FAILED = "FAILED";

    /** 超级管理员权限码 */
    private static final String SUPER_ADMIN = "super_admin";

    /** JSON 序列化器 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentProperties properties;

    private final AgentOperationMapper operationMapper;

    private final OperationInvoker operationInvoker;

    /**
     * 构造确认服务。
     *
     * @param properties       Agent 配置
     * @param operationMapper  确认记录 Mapper
     * @param operationInvoker 写操作执行器
     */
    public PlatformOpService(AgentProperties properties, AgentOperationMapper operationMapper,
                             OperationInvoker operationInvoker) {
        this.properties = properties;
        this.operationMapper = operationMapper;
        this.operationInvoker = operationInvoker;
    }

    /**
     * 创建确认令牌（仅落库，不执行下游写操作）。
     *
     * @param operationName  操作名
     * @param params         操作参数
     * @param userId         发起用户ID
     * @param conversationId 来源会话ID（可空）
     * @return 确认卡片数据（confirmationId/operation/summary/permission/expiresAt/status）
     */
    public Map<String, Object> create(String operationName, Map<String, Object> params,
                                      Long userId, Long conversationId) {
        ensureEnabled();
        PlatformOperationCatalog.Operation operation = PlatformOperationCatalog.require(operationName);
        Map<String, Object> body = PlatformOperationCatalog.normalizeParams(operation, params);
        AgentOperation record = new AgentOperation();
        record.setConfirmationId(UUID.randomUUID().toString());
        record.setUserId(userId);
        record.setConversationId(conversationId);
        record.setOperation(operation.name());
        record.setParamsJson(writeJson(body));
        record.setParamsHash(sha256(record.getParamsJson()));
        record.setPermission(operation.permission());
        record.setTarget(operation.target());
        record.setStatus(STATUS_PENDING);
        record.setExpiresAt(LocalDateTime.now().plusSeconds(Math.max(1, properties.getOps().getTtlSeconds())));
        operationMapper.insert(record);
        log.info("平台操作待确认: confirmationId={}, operation={}, userId={}",
                record.getConfirmationId(), operation.name(), userId);
        return card(record, operation, body);
    }

    /**
     * 确认并执行写操作（一次性、同用户、未过期、具备操作权限）。
     *
     * @param confirmationId  确认令牌ID
     * @param userId          当前用户ID
     * @param permissionCodes 当前用户权限码（逗号分隔）
     * @return 下游响应数据
     */
    public Map<String, Object> confirm(String confirmationId, Long userId, String permissionCodes) {
        ensureEnabled();
        AgentOperation record = requireRecord(confirmationId);
        requireOwner(record, userId);
        if (isExpired(record)) {
            markExpired(record);
            throw new BizException(410, "确认令牌已过期，请重新发起");
        }
        PlatformOperationCatalog.Operation operation = PlatformOperationCatalog.require(record.getOperation());
        requirePermission(operation, permissionCodes);
        if (claim(record, userId) == 0) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT.getCode(), "确认令牌已使用或已失效");
        }
        Map<String, Object> body = readJson(record.getParamsJson());
        String url = targetUrl(operation);
        long startedMillis = System.currentTimeMillis();
        try {
            Map<String, Object> data = operationInvoker.invoke(url, body, userId, permissionCodes);
            finish(record, STATUS_SUCCESS, writeJson(data), null, 0, elapsed(startedMillis));
            log.info("平台操作执行成功: confirmationId={}, operation={}", confirmationId, operation.name());
            return data;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finish(record, STATUS_FAILED, null, "执行被中断", ErrorCode.SYSTEM_ERROR.getCode(), elapsed(startedMillis));
            throw new BizException("平台操作执行被中断");
        } catch (IOException | RuntimeException e) {
            finish(record, STATUS_FAILED, null, e.getMessage(), ErrorCode.SYSTEM_ERROR.getCode(),
                    elapsed(startedMillis));
            log.warn("平台操作执行失败: confirmationId={}, operation={}", confirmationId, operation.name(), e);
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "平台操作执行失败：" + e.getMessage());
        }
    }

    /**
     * 拒绝执行写操作。
     *
     * @param confirmationId 确认令牌ID
     * @param userId         当前用户ID
     * @return 最新状态（status=REJECTED）
     */
    public Map<String, Object> reject(String confirmationId, Long userId) {
        ensureEnabled();
        AgentOperation record = requireRecord(confirmationId);
        requireOwner(record, userId);
        if (STATUS_REJECTED.equals(record.getStatus())) {
            return statusOf(record);
        }
        LambdaUpdateWrapper<AgentOperation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentOperation::getId, record.getId());
        wrapper.eq(AgentOperation::getStatus, STATUS_PENDING);
        wrapper.set(AgentOperation::getStatus, STATUS_REJECTED);
        wrapper.set(AgentOperation::getConfirmedBy, userId);
        wrapper.set(AgentOperation::getConfirmedAt, LocalDateTime.now());
        if (operationMapper.update(null, wrapper) == 0) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT.getCode(), "确认令牌已失效");
        }
        record.setStatus(STATUS_REJECTED);
        record.setConfirmedBy(userId);
        return statusOf(record);
    }

    /**
     * 查询确认记录（不存在时抛 404）。
     *
     * @param confirmationId 确认令牌ID
     * @return 记录
     */
    private AgentOperation requireRecord(String confirmationId) {
        if (!StringUtils.hasText(confirmationId)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "确认令牌不能为空");
        }
        LambdaQueryWrapper<AgentOperation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentOperation::getConfirmationId, confirmationId);
        AgentOperation record = operationMapper.selectOne(wrapper);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "确认令牌不存在");
        }
        return record;
    }

    /**
     * 校验令牌归属用户。
     *
     * @param record 记录
     * @param userId 当前用户ID
     */
    private static void requireOwner(AgentOperation record, Long userId) {
        if (!Objects.equals(record.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "无权确认他人的平台操作");
        }
    }

    /**
     * 校验用户具备操作权限。
     *
     * @param operation       操作定义
     * @param permissionCodes 权限码串
     */
    private static void requirePermission(PlatformOperationCatalog.Operation operation, String permissionCodes) {
        Set<String> codes = WebHeaders.splitCodes(permissionCodes);
        if (!codes.contains(operation.permission()) && !codes.contains(SUPER_ADMIN)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "缺少操作权限：" + operation.permission());
        }
    }

    /**
     * 原子占用令牌：仅 {@code PENDING} 且未过期时置为 {@code CONFIRMED}。
     *
     * @param record 记录
     * @param userId 确认人用户ID
     * @return 影响行数（0 表示已被使用或已过期）
     */
    private int claim(AgentOperation record, Long userId) {
        LambdaUpdateWrapper<AgentOperation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentOperation::getId, record.getId());
        wrapper.eq(AgentOperation::getStatus, STATUS_PENDING);
        wrapper.gt(AgentOperation::getExpiresAt, LocalDateTime.now());
        wrapper.set(AgentOperation::getStatus, STATUS_CONFIRMED);
        wrapper.set(AgentOperation::getConfirmedBy, userId);
        wrapper.set(AgentOperation::getConfirmedAt, LocalDateTime.now());
        int rows = operationMapper.update(null, wrapper);
        if (rows > 0) {
            record.setStatus(STATUS_CONFIRMED);
            record.setConfirmedBy(userId);
        }
        return rows;
    }

    /**
     * 标记过期（仅 {@code PENDING} 生效）。
     *
     * @param record 记录
     */
    private void markExpired(AgentOperation record) {
        LambdaUpdateWrapper<AgentOperation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentOperation::getId, record.getId());
        wrapper.eq(AgentOperation::getStatus, STATUS_PENDING);
        wrapper.set(AgentOperation::getStatus, STATUS_EXPIRED);
        operationMapper.update(null, wrapper);
    }

    /**
     * 回写最终执行状态。
     *
     * @param record    记录
     * @param status    最终状态
     * @param result    结果 JSON（成功时）
     * @param errorMsg  错误信息（失败时）
     * @param resultCode 结果码（0 成功 / 500 失败）
     * @param elapsedMs 执行耗时（毫秒）
     */
    private void finish(AgentOperation record, String status, String result, String errorMsg,
                        Integer resultCode, Integer elapsedMs) {
        LambdaUpdateWrapper<AgentOperation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentOperation::getId, record.getId());
        wrapper.set(AgentOperation::getStatus, status);
        wrapper.set(AgentOperation::getResultJson, result);
        wrapper.set(AgentOperation::getErrorMsg, truncate(errorMsg));
        wrapper.set(AgentOperation::getResultCode, resultCode);
        wrapper.set(AgentOperation::getElapsedMs, elapsedMs);
        operationMapper.update(null, wrapper);
        record.setStatus(status);
        record.setResultJson(result);
        record.setResultCode(resultCode);
        record.setElapsedMs(elapsedMs);
    }

    /**
     * 计算执行耗时。
     *
     * @param startedMillis 开始时间戳（毫秒）
     * @return 耗时毫秒数
     */
    private static int elapsed(long startedMillis) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedMillis);
    }

    /**
     * 判断令牌是否已过期（以库内 {@code expiresAt} 为准）。
     *
     * @param record 记录
     * @return 是否过期
     */
    private static boolean isExpired(AgentOperation record) {
        return record.getExpiresAt() == null || LocalDateTime.now().isAfter(record.getExpiresAt());
    }

    /**
     * 拼接下游目标地址。
     *
     * @param operation 操作定义
     * @return 完整 URL
     */
    private String targetUrl(PlatformOperationCatalog.Operation operation) {
        String baseUrl = properties.getOps().getTargets().get(operation.target());
        if (!StringUtils.hasText(baseUrl)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "未配置目标服务：" + operation.target());
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized + operation.path();
    }

    /**
     * 校验开关。
     */
    private void ensureEnabled() {
        if (!properties.getOps().isEnabled()) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "平台操作未启用");
        }
    }

    /**
     * 构造确认卡片数据。
     *
     * @param record    记录
     * @param operation 操作定义
     * @param params    参数
     * @return 卡片数据
     */
    private static Map<String, Object> card(AgentOperation record, PlatformOperationCatalog.Operation operation,
                                            Map<String, Object> params) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("confirmationId", record.getConfirmationId());
        card.put("operation", operation.name());
        card.put("summary", operation.summary(params));
        card.put("permission", operation.permission());
        card.put("expiresAt", record.getExpiresAt().toString());
        card.put("status", record.getStatus());
        return card;
    }

    /**
     * 只返回状态信息（拒绝/幂等场景）。
     *
     * @param record 记录
     * @return 状态数据
     */
    private static Map<String, Object> statusOf(AgentOperation record) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("confirmationId", record.getConfirmationId());
        result.put("operation", record.getOperation());
        result.put("status", record.getStatus());
        return result;
    }

    /**
     * 序列化 JSON。
     *
     * @param value 对象
     * @return JSON 文本
     */
    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "参数序列化失败");
        }
    }

    /**
     * 反序列化参数 JSON。
     *
     * @param json JSON 文本
     * @return 参数
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(String json) {
        try {
            return json == null ? new LinkedHashMap<>() : MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "参数解析失败");
        }
    }

    /**
     * 计算 SHA-256 十六进制摘要。
     *
     * @param text 原文
     * @return 摘要
     */
    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 截断错误信息到列宽。
     *
     * @param message 原文
     * @return 截断结果
     */
    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
