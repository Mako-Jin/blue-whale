package com.bluewhale.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.contract.client.WorkflowInternalClient;
import com.bluewhale.contract.dto.EsignCallbackPayload;
import com.bluewhale.contract.esign.EsignHashUtils;
import com.bluewhale.contract.esign.FileClient;
import com.bluewhale.contract.esign.provider.EsignProvider;
import com.bluewhale.contract.esign.provider.EsignProviderRegistry;
import com.bluewhale.contract.entity.CtContract;
import com.bluewhale.contract.entity.CtEsignCallback;
import com.bluewhale.contract.entity.CtSignRecord;
import com.bluewhale.contract.entity.CtSignTask;
import com.bluewhale.contract.mapper.CtContractMapper;
import com.bluewhale.contract.mapper.CtEsignCallbackMapper;
import com.bluewhale.contract.mapper.CtSignRecordMapper;
import com.bluewhale.contract.mapper.CtSignTaskMapper;
import com.bluewhale.contract.service.EsignCallbackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 电子签章回调服务实现。
 *
 * <p>回调安全与幂等：HMAC/RSA 验签 + 时间戳窗口（渠道实现内完成）→
 * {@code (provider, provider_event_id)} 唯一键防重放 → 任务已终态时直接返回 →
 * 事务内写签署记录/更新合同/推进工作流，失败标记 {@code FAILED} 并允许重放。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsignCallbackServiceImpl implements EsignCallbackService {

    /** 回调状态：已接收 */
    private static final String CALLBACK_RECEIVED = "RECEIVED";

    /** 回调状态：已处理 */
    private static final String CALLBACK_PROCESSED = "PROCESSED";

    /** 回调状态：处理失败 */
    private static final String CALLBACK_FAILED = "FAILED";

    /** 动作：签署完成 */
    private static final String ACTION_SIGN = "SIGN";

    /** 动作：拒签 */
    private static final String ACTION_REFUSE = "REFUSE";

    /** 工作流动作：同意 */
    private static final String WORKFLOW_APPROVE = "APPROVE";

    /** 工作流动作：驳回 */
    private static final String WORKFLOW_REJECT = "REJECT";

    /** 操作来源 */
    private static final String OPERATOR_SOURCE = "ESIGN_CALLBACK";

    /** 任务状态 */
    private static final String TASK_PENDING = "PENDING";

    /** 任务状态 */
    private static final String TASK_SIGNING = "SIGNING";

    /** 任务状态 */
    private static final String TASK_SIGNED = "SIGNED";

    /** 任务状态 */
    private static final String TASK_REFUSED = "REFUSED";

    /** 任务状态 */
    private static final String TASK_EXPIRED = "EXPIRED";

    /** 合同状态 */
    private static final String CONTRACT_SIGNING = "SIGNING";

    /** 合同状态 */
    private static final String CONTRACT_SIGNED = "SIGNED";

    /** 失败原因最大长度 */
    private static final int MAX_ERROR_LENGTH = 500;

    /** JSON 解析器（线程安全） */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final EsignProviderRegistry providerRegistry;

    private final CtEsignCallbackMapper callbackMapper;

    private final CtSignTaskMapper signTaskMapper;

    private final CtSignRecordMapper signRecordMapper;

    private final CtContractMapper contractMapper;

    private final FileClient fileClient;

    private final WorkflowInternalClient workflowInternalClient;

    private final TransactionTemplate transactionTemplate;

    @Override
    public void handle(String providerName, String rawBody, Map<String, String> headers) {
        EsignProvider provider = providerRegistry.require(providerName);
        if (!provider.verifyCallback(rawBody, headers)) {
            log.warn("电子签章回调验签失败: provider={}", provider.name());
            throw new BizException(ErrorCode.FORBIDDEN, "回调验签失败或时间戳超出允许窗口");
        }
        EsignCallbackPayload payload = parsePayload(rawBody);
        String eventId = payload.getProviderEventId().trim();
        String action = payload.getAction().trim().toUpperCase(Locale.ROOT);
        if (!ACTION_SIGN.equals(action) && !ACTION_REFUSE.equals(action)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的回调动作: " + payload.getAction());
        }
        CtEsignCallback existing = findCallback(provider.name(), eventId);
        if (existing != null && CALLBACK_PROCESSED.equals(existing.getStatus())) {
            log.info("电子签章回调已处理，幂等返回: provider={}, eventId={}", provider.name(), eventId);
            return;
        }
        String payloadHash = EsignHashUtils.sha256(rawBody == null ? "" : rawBody);
        Long callbackId = prepareCallback(existing, provider.name(), eventId, payloadHash, rawBody, payload);
        if (callbackId == null) {
            log.info("电子签章回调并发重复，幂等返回: provider={}, eventId={}", provider.name(), eventId);
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> process(provider, payload, action, payloadHash, rawBody));
        } catch (RuntimeException ex) {
            markCallback(callbackId, CALLBACK_FAILED, ex.getMessage());
            throw ex;
        }
        markCallback(callbackId, CALLBACK_PROCESSED, null);
    }

    /**
     * 解析回调报文。
     *
     * @param rawBody 原始报文
     * @return 回调载荷
     */
    private EsignCallbackPayload parsePayload(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "回调报文为空");
        }
        EsignCallbackPayload payload;
        try {
            payload = JSON.readValue(rawBody, EsignCallbackPayload.class);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "回调报文解析失败");
        }
        if (payload == null || !StringUtils.hasText(payload.getProviderEventId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "回调缺少事件ID");
        }
        if (payload.getProviderEventId().trim().length() > 128) {
            throw new BizException(ErrorCode.PARAM_ERROR, "回调事件ID超长");
        }
        if (!StringUtils.hasText(payload.getAction())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "回调缺少动作");
        }
        return payload;
    }

    /**
     * 查询既有回调记录。
     *
     * @param provider 渠道
     * @param eventId  事件ID
     * @return 回调记录；不存在返回 {@code null}
     */
    private CtEsignCallback findCallback(String provider, String eventId) {
        return callbackMapper.selectOne(new LambdaQueryWrapper<CtEsignCallback>()
                .eq(CtEsignCallback::getProvider, provider.toUpperCase(Locale.ROOT))
                .eq(CtEsignCallback::getProviderEventId, eventId)
                .last("LIMIT 1"));
    }

    /**
     * 落库回调记录（新建或重置为 RECEIVED）。
     *
     * @return 回调记录ID；并发重复且已处理时返回 {@code null}
     */
    private Long prepareCallback(CtEsignCallback existing, String provider, String eventId, String payloadHash,
                                 String rawBody, EsignCallbackPayload payload) {
        if (existing != null) {
            existing.setStatus(CALLBACK_RECEIVED);
            existing.setRetryCount(existing.getRetryCount() == null ? 1 : existing.getRetryCount() + 1);
            existing.setErrorMsg(null);
            existing.setPayloadHash(payloadHash);
            existing.setPayload(rawBody);
            callbackMapper.updateById(existing);
            return existing.getId();
        }
        CtEsignCallback row = new CtEsignCallback();
        row.setProvider(provider.toUpperCase(Locale.ROOT));
        row.setProviderEventId(eventId);
        row.setSignTaskId(parseLong(payload.getSignTaskId()));
        row.setPayloadHash(payloadHash);
        row.setPayload(rawBody);
        row.setStatus(CALLBACK_RECEIVED);
        row.setRetryCount(0);
        try {
            callbackMapper.insert(row);
            return row.getId();
        } catch (DuplicateKeyException ex) {
            CtEsignCallback concurrent = findCallback(provider, eventId);
            if (concurrent == null || CALLBACK_PROCESSED.equals(concurrent.getStatus())) {
                return null;
            }
            concurrent.setRetryCount(concurrent.getRetryCount() == null ? 1 : concurrent.getRetryCount() + 1);
            concurrent.setStatus(CALLBACK_RECEIVED);
            concurrent.setErrorMsg(null);
            callbackMapper.updateById(concurrent);
            return concurrent.getId();
        }
    }

    /**
     * 事务内处理回调：更新签署任务/合同、写证据、推进工作流。
     */
    private void process(EsignProvider provider, EsignCallbackPayload payload, String action,
                         String payloadHash, String rawBody) {
        CtSignTask task = locateTask(provider, payload);
        CtContract contract = requireContract(task.getContractId());
        if (ACTION_REFUSE.equals(action)) {
            handleRefusal(task, payload, provider);
            return;
        }
        if (TASK_SIGNED.equals(task.getStatus())) {
            log.info("签署任务已完成，忽略重复签署回调: taskId={}", task.getId());
            return;
        }
        if (!TASK_PENDING.equals(task.getStatus()) && !TASK_SIGNING.equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "签署任务状态不可签署: " + task.getStatus());
        }
        String providerTaskId = StringUtils.hasText(payload.getProviderTaskId())
                ? payload.getProviderTaskId() : task.getProviderTaskId();
        byte[] downloaded = tryDownload(provider, providerTaskId);
        Long signedFileId;
        String signedSha256;
        if (downloaded != null) {
            signedSha256 = EsignHashUtils.sha256(downloaded);
            signedFileId = fileClient.upload(signedFilename(contract, provider), "application/pdf", downloaded);
        } else {
            signedSha256 = isSha256(payload.getSignedSha256())
                    ? payload.getSignedSha256().trim() : payloadHash;
            signedFileId = parseLong(payload.getSignedFileId());
        }
        String originalSha256 = baseSha256(contract, rawBody);
        LocalDateTime signedAt = parseSignedAt(payload.getSignedAt());
        String evidenceNo = StringUtils.hasText(payload.getEvidenceNo())
                ? payload.getEvidenceNo() : "EV-" + task.getId();
        String chainHash = EsignHashUtils.chain(latestChain(contract.getId()), contract.getId(),
                task.getVersionId(), task.getSignerUserId(), signedAt.toString(),
                originalSha256, signedSha256, evidenceNo);

        CtSignRecord record = new CtSignRecord();
        record.setContractId(contract.getId());
        record.setSignTaskId(task.getId());
        record.setSignerUserId(task.getSignerUserId());
        record.setCertSerial(payload.getCertSerial());
        record.setSignedAt(signedAt);
        record.setOriginalSha256(originalSha256);
        record.setSignedSha256(signedSha256);
        record.setAlgorithm(provider.name().toUpperCase(Locale.ROOT) + "-REMOTE");
        record.setEvidenceNo(evidenceNo);
        record.setChainHash(chainHash);
        record.setRealNameStatus(StringUtils.hasText(payload.getRealNameStatus())
                ? payload.getRealNameStatus().trim() : "UNVERIFIED");
        record.setRealNameMethod(payload.getRealNameMethod());
        record.setRealNameEvidenceNo(payload.getRealNameEvidenceNo());
        record.setWillingnessMethod(payload.getWillingnessMethod());
        record.setWillingnessEvidenceNo(payload.getWillingnessEvidenceNo());
        LocalDateTime willingnessAt = parseSignedAt(payload.getWillingnessAt());
        if (willingnessAt == null && StringUtils.hasText(payload.getWillingnessEvidenceNo())) {
            willingnessAt = signedAt;
        }
        record.setWillingnessAt(willingnessAt);
        signRecordMapper.insert(record);

        task.setStatus(TASK_SIGNED);
        task.setSignedAt(signedAt);
        task.setSignedFileId(signedFileId);
        if (StringUtils.hasText(providerTaskId)) {
            task.setProviderTaskId(providerTaskId);
        }
        signTaskMapper.updateById(task);

        contract.setFileId(signedFileId);
        boolean allSigned = listTasks(contract.getId()).stream()
                .filter(item -> !TASK_EXPIRED.equals(item.getStatus()))
                .allMatch(item -> TASK_SIGNED.equals(item.getStatus()));
        contract.setStatus(allSigned ? CONTRACT_SIGNED : CONTRACT_SIGNING);
        contractMapper.updateById(contract);

        advanceWorkflow(task, WORKFLOW_APPROVE, "三方签署完成: " + evidenceNo, payload.getProviderEventId());
        log.info("三方签署回调处理完成: taskId={}, contractId={}, evidenceNo={}, provider={}",
                task.getId(), contract.getId(), evidenceNo, provider.name());
    }

    /**
     * 处理三方拒签回调：更新任务状态并推进工作流。
     *
     * @param task    签署任务
     * @param payload 回调载荷
     * @param provider 签章渠道
     */
    private void handleRefusal(CtSignTask task, EsignCallbackPayload payload, EsignProvider provider) {
        if (TASK_SIGNED.equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "签署任务已完成，不能再拒签");
        }
        String reason = StringUtils.hasText(payload.getRefuseReason())
                ? payload.getRefuseReason() : "三方拒签";
        task.setStatus(TASK_REFUSED);
        task.setRefuseReason(reason);
        if (StringUtils.hasText(payload.getProviderTaskId())) {
            task.setProviderTaskId(payload.getProviderTaskId());
        }
        signTaskMapper.updateById(task);
        advanceWorkflow(task, WORKFLOW_REJECT, "三方拒签: " + reason, payload.getProviderEventId());
        log.info("三方拒签回调处理完成: taskId={}, provider={}", task.getId(), provider.name());
    }

    /**
     * 定位签署任务：优先平台任务ID，其次三方任务号。
     */
    private CtSignTask locateTask(EsignProvider provider, EsignCallbackPayload payload) {
        Long signTaskId = parseLong(payload.getSignTaskId());
        CtSignTask task;
        if (signTaskId != null) {
            task = signTaskMapper.selectById(signTaskId);
        } else if (StringUtils.hasText(payload.getProviderTaskId())) {
            task = signTaskMapper.selectOne(new LambdaQueryWrapper<CtSignTask>()
                    .eq(CtSignTask::getProviderTaskId, payload.getProviderTaskId())
                    .last("LIMIT 1"));
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR, "回调缺少 signTaskId 或 providerTaskId");
        }
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "签署任务不存在");
        }
        String taskProvider = task.getProvider() == null ? "" : task.getProvider();
        if (!provider.name().equalsIgnoreCase(taskProvider)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "回调渠道与签署任务不一致: " + provider.name());
        }
        return task;
    }

    /**
     * 推进关联工作流任务（未关联时跳过）。
     */
    private void advanceWorkflow(CtSignTask task, String action, String comment, String eventId) {
        if (task.getWorkflowTaskId() == null) {
            return;
        }
        workflowInternalClient.complete(task.getWorkflowTaskId(), task.getSignerUserId(), action,
                comment, eventId, OPERATOR_SOURCE);
    }

    /**
     * 尝试下载三方签章件，未接入时返回 {@code null}。
     */
    private byte[] tryDownload(EsignProvider provider, String providerTaskId) {
        if (!StringUtils.hasText(providerTaskId)) {
            return null;
        }
        try {
            byte[] content = provider.downloadSignedFile(providerTaskId);
            return content == null || content.length == 0 ? null : content;
        } catch (Exception ex) {
            log.info("三方签章件下载不可用，使用回调哈希兜底: provider={}, reason={}", provider.name(), ex.getMessage());
            return null;
        }
    }

    /**
     * 计算签署底稿摘要：优先合同正文，缺失时用回调原文兜底。
     */
    private String baseSha256(CtContract contract, String rawBody) {
        Long fileId = contract.getFileId();
        if (fileId != null) {
            try {
                return EsignHashUtils.sha256(fileClient.download(fileId));
            } catch (Exception ex) {
                log.info("合同正文下载失败，使用回调原文计算底稿摘要: contractId={}, reason={}",
                        contract.getId(), ex.getMessage());
            }
        }
        return EsignHashUtils.sha256(rawBody == null ? "" : rawBody);
    }

    /**
     * 解析签署时间，非法时回落到当前时间。
     */
    private LocalDateTime parseSignedAt(String value) {
        if (StringUtils.hasText(value)) {
            try {
                return LocalDateTime.parse(value.trim());
            } catch (DateTimeParseException ex) {
                log.info("回调签署时间格式非法，使用当前时间: {}", value);
            }
        }
        return LocalDateTime.now().withNano(0);
    }

    /**
     * 查询最新链式哈希。
     */
    private String latestChain(Long contractId) {
        List<CtSignRecord> records = signRecordMapper.selectList(new LambdaQueryWrapper<CtSignRecord>()
                .eq(CtSignRecord::getContractId, contractId)
                .orderByAsc(CtSignRecord::getSignedAt)
                .orderByAsc(CtSignRecord::getId));
        return records.isEmpty() ? "" : records.get(records.size() - 1).getChainHash();
    }

    /**
     * 查询合同全部签署任务。
     */
    private List<CtSignTask> listTasks(Long contractId) {
        return signTaskMapper.selectList(new LambdaQueryWrapper<CtSignTask>()
                .eq(CtSignTask::getContractId, contractId)
                .orderByAsc(CtSignTask::getSeqNo)
                .orderByAsc(CtSignTask::getId));
    }

    /**
     * 更新回调处理状态。
     */
    private void markCallback(Long callbackId, String status, String errorMsg) {
        if (callbackId == null) {
            return;
        }
        CtEsignCallback row = callbackMapper.selectById(callbackId);
        if (row == null) {
            return;
        }
        row.setStatus(status);
        row.setErrorMsg(truncate(errorMsg));
        callbackMapper.updateById(row);
    }

    /**
     * 校验合同存在。
     */
    private CtContract requireContract(Long id) {
        CtContract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "合同不存在: " + id);
        }
        return contract;
    }

    /**
     * 生成签章件文件名。
     */
    private String signedFilename(CtContract contract, EsignProvider provider) {
        Integer version = contract.getCurrentVersion() == null ? 1 : contract.getCurrentVersion();
        return "contract-" + contract.getCode() + "-v" + version + "-"
                + provider.name() + "-signed.pdf";
    }

    /**
     * 判断是否为 SHA-256 十六进制串。
     */
    private boolean isSha256(String value) {
        return StringUtils.hasText(value) && value.trim().matches("^[0-9a-fA-F]{64}$");
    }

    /**
     * 解析ID文本。
     */
    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 截断错误信息到列长度。
     */
    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
