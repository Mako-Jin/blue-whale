package com.bluewhale.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.contract.dto.OperatorContext;
import com.bluewhale.contract.dto.SignExecuteRequest;
import com.bluewhale.contract.dto.SignRefuseRequest;
import com.bluewhale.contract.dto.SignStartRequest;
import com.bluewhale.contract.dto.SignTaskCreateRequest;
import com.bluewhale.contract.esign.EsignHashUtils;
import com.bluewhale.contract.esign.FileClient;
import com.bluewhale.contract.esign.PdfStampService;
import com.bluewhale.contract.esign.SignatureService;
import com.bluewhale.contract.esign.provider.EsignProvider;
import com.bluewhale.contract.esign.provider.EsignProviderRegistry;
import com.bluewhale.contract.esign.provider.EsignTaskCommand;
import com.bluewhale.contract.esign.provider.EsignTaskResult;
import com.bluewhale.contract.entity.CtContract;
import com.bluewhale.contract.entity.CtContractParty;
import com.bluewhale.contract.entity.CtContractVersion;
import com.bluewhale.contract.entity.CtSeal;
import com.bluewhale.contract.entity.CtSealGrant;
import com.bluewhale.contract.entity.CtSignRecord;
import com.bluewhale.contract.entity.CtSignTask;
import com.bluewhale.contract.mapper.CtContractMapper;
import com.bluewhale.contract.mapper.CtContractPartyMapper;
import com.bluewhale.contract.mapper.CtContractVersionMapper;
import com.bluewhale.contract.mapper.CtSealGrantMapper;
import com.bluewhale.contract.mapper.CtSealMapper;
import com.bluewhale.contract.mapper.CtSignRecordMapper;
import com.bluewhale.contract.mapper.CtSignTaskMapper;
import com.bluewhale.contract.service.SignService;
import com.bluewhale.contract.vo.EvidenceVO;
import com.bluewhale.contract.vo.SignRecordVO;
import com.bluewhale.contract.vo.SignResultVO;
import com.bluewhale.contract.vo.SignTaskVO;
import com.bluewhale.contract.vo.VerifyResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 合同签署服务实现。
 *
 * <p>核心规则：用章授权不通过抛 {@link ErrorCode#FORBIDDEN}（控制器审计留痕）；
 * 同一任务重复签署返回既有记录（幂等）；全部任务签署后合同转 SIGNED，拒签时合同停留 SIGNING。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignServiceImpl implements SignService {

    /** 合同状态：审批通过 */
    private static final String CONTRACT_APPROVED = "APPROVED";

    /** 合同状态：签署中 */
    private static final String CONTRACT_SIGNING = "SIGNING";

    /** 合同状态：已签署 */
    private static final String CONTRACT_SIGNED = "SIGNED";

    /** 合同状态：已生效 */
    private static final String CONTRACT_EFFECTIVE = "EFFECTIVE";

    /** 合同状态：已作废 */
    private static final String CONTRACT_VOID = "VOID";

    /** 任务状态：待签 */
    private static final String TASK_PENDING = "PENDING";

    /** 任务状态：签署中（已推送三方待签） */
    private static final String TASK_SIGNING = "SIGNING";

    /** 任务状态：已签 */
    private static final String TASK_SIGNED = "SIGNED";

    /** 任务状态：已拒签 */
    private static final String TASK_REFUSED = "REFUSED";

    /** 任务状态：已失效 */
    private static final String TASK_EXPIRED = "EXPIRED";

    /** 实名状态：未实名（仅账号登录会话，未接第三方实名） */
    private static final String REAL_NAME_UNVERIFIED = "UNVERIFIED";

    /** 实名方式：登录会话 */
    private static final String METHOD_LOGIN_SESSION = "LOGIN_SESSION";

    /** 意愿方式：签署页勾选确认 */
    private static final String METHOD_SIGN_CONFIRM = "SIGN_CONFIRM";

    /** 印章/授权状态：启用 */
    private static final String STATUS_ENABLED = "ENABLED";

    /** 本地签署渠道 */
    private static final String PROVIDER_LOCAL = "local";

    private final CtContractMapper contractMapper;

    private final CtContractPartyMapper partyMapper;

    private final CtContractVersionMapper versionMapper;

    private final CtSealMapper sealMapper;

    private final CtSealGrantMapper grantMapper;

    private final CtSignTaskMapper signTaskMapper;

    private final CtSignRecordMapper signRecordMapper;

    private final FileClient fileClient;

    private final PdfStampService pdfStampService;

    private final SignatureService signatureService;

    private final EsignProviderRegistry providerRegistry;

    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SignTaskVO> start(Long contractId, SignStartRequest request, OperatorContext operator) {
        CtContract contract = requireContract(contractId);
        String status = contract.getStatus();
        if (!CONTRACT_APPROVED.equals(status) && !CONTRACT_SIGNING.equals(status)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.contract.service.approved-signing-contracts-can-start-signing",
                          "仅审批通过或签署中的合同可发起签署，当前状态: {0}", status);
        }
        List<SignTaskCreateRequest> specs = resolveSpecs(contract, request);
        if (specs.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.contract.service.signable-counterparty-found-register-signer-in",
                          "未找到可签署的相对方，请在合同中登记签署人");
        }
        List<CtSignTask> existing = listTasks(contractId);
        int maxSeq = existing.stream()
                .map(CtSignTask::getSeqNo)
                .filter(seq -> seq != null)
                .max(Integer::compareTo)
                .orElse(0);
        Long versionId = currentVersionId(contract);
        EsignProvider esignProvider = request == null || !StringUtils.hasText(request.getProvider())
                ? providerRegistry.defaultProvider() : providerRegistry.require(request.getProvider());
        String provider = esignProvider.name().toUpperCase(Locale.ROOT);
        boolean thirdParty = !PROVIDER_LOCAL.equalsIgnoreCase(esignProvider.name());
        byte[] contractFile = null;
        String contractFileName = null;
        int created = 0;
        for (SignTaskCreateRequest spec : specs) {
            if (alreadyCovered(existing, spec)) {
                continue;
            }
            CtSignTask task = new CtSignTask();
            task.setContractId(contractId);
            task.setVersionId(versionId);
            task.setSeqNo(++maxSeq);
            task.setSignerUserId(spec.getSignerUserId());
            task.setSignerParty(spec.getSignerParty());
            task.setSignerName(spec.getSignerName());
            task.setWorkflowTaskId(spec.getWorkflowTaskId());
            task.setProvider(provider);
            task.setStatus(TASK_PENDING);
            signTaskMapper.insert(task);
            if (thirdParty) {
                if (contractFile == null) {
                    contractFile = loadBasePdf(contract, versionId);
                    int versionNo = contract.getCurrentVersion() == null ? 1 : contract.getCurrentVersion();
                    contractFileName = "contract-" + contract.getCode() + "-v" + versionNo + "-unsigned.pdf";
                }
                EsignTaskResult esignTask = esignProvider.createTask(new EsignTaskCommand(task.getId(),
                        contract.getId(), contract.getCode(), contract.getTitle(),
                        task.getSignerName(), task.getSignerParty(), contractFile, contractFileName));
                task.setProviderTaskId(esignTask.providerTaskId());
                task.setSignUrl(esignTask.signUrl());
                task.setStatus(TASK_SIGNING);
                signTaskMapper.updateById(task);
            }
            existing.add(task);
            created++;
        }
        if (!CONTRACT_SIGNING.equals(contract.getStatus())) {
            contract.setStatus(CONTRACT_SIGNING);
            contractMapper.updateById(contract);
        }
        log.info("合同发起签署: contractId={}, 新增任务={}, 操作人={}", contractId, created,
                operator == null ? null : operator.userId());
        return existing.stream()
                .sorted(Comparator.comparing(CtSignTask::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .map(task -> toTaskVo(task, contract))
                .toList();
    }

    @Override
    public List<SignTaskVO> tasks(Long contractId) {
        CtContract contract = requireContract(contractId);
        return listTasks(contractId).stream()
                .sorted(Comparator.comparing(CtSignTask::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .map(task -> toTaskVo(task, contract))
                .toList();
    }

    @Override
    public List<SignTaskVO> pending(OperatorContext operator) {
        Long userId = parseUserId(operator);
        if (userId == null) {
            return List.of();
        }
        List<CtSignTask> tasks = signTaskMapper.selectList(new LambdaQueryWrapper<CtSignTask>()
                .eq(CtSignTask::getSignerUserId, userId)
                .in(CtSignTask::getStatus, List.of(TASK_PENDING, "SIGNING"))
                .orderByDesc(CtSignTask::getCreateTime));
        List<SignTaskVO> result = new ArrayList<>(tasks.size());
        for (CtSignTask task : tasks) {
            CtContract contract = contractMapper.selectById(task.getContractId());
            if (contract == null) {
                continue;
            }
            SignTaskVO vo = toTaskVo(task, contract);
            vo.setContractCode(contract.getCode());
            vo.setContractTitle(contract.getTitle());
            vo.setContractAmount(contract.getAmount());
            vo.setContractCategory(contract.getCategory());
            result.add(vo);
        }
        return result;
    }

    @Override
    public SignResultVO sign(Long taskId, SignExecuteRequest request, OperatorContext operator, String clientIp) {
        CtSignTask task = requireTask(taskId);
        Long operatorId = requireOperatorId(operator);
        if (TASK_SIGNED.equals(task.getStatus())) {
            CtSignRecord existing = signRecordMapper.selectOne(new LambdaQueryWrapper<CtSignRecord>()
                    .eq(CtSignRecord::getSignTaskId, taskId));
            if (existing != null) {
                CtContract contract = requireContract(task.getContractId());
                log.info("签署任务已签署，幂等返回: taskId={}", taskId);
                SignResultVO idempotent = toResult(task, existing, contract.getStatus());
                idempotent.setStampedPages(null);
                return idempotent;
            }
        }
        if (!Boolean.TRUE.equals(request.getConfirmWillingness())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.contract.service.confirm-signing-intent-submitting", "请确认签署意愿后再提交");
        }
        if (TASK_REFUSED.equals(task.getStatus()) || TASK_EXPIRED.equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.contract.service.signing-task-status-cannot-sign", "签署任务状态不可签署: {0}",
                          task.getStatus());
        }
        CtContract contract = requireContract(task.getContractId());
        if (!CONTRACT_SIGNING.equals(contract.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.contract.service.status-contract-allow-sign", "合同当前状态不可签署: {0}", contract.getStatus());
        }
        if (task.getSignerUserId() != null && !task.getSignerUserId().equals(operatorId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "biz.contract.service.signer-self-can-execute-sign", "仅签署人本人可执行签署");
        }
        CtSeal seal = sealMapper.selectById(request.getSealId());
        if (seal == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.contract.service.seal-found", "印章不存在: {0}", request.getSealId());
        }
        assertSealUsable(seal);
        assertGranted(seal, contract, operatorId);

        PreparedArtifact artifact = prepareArtifact(contract, task, seal, request, operatorId);
        SignResultVO result = transactionTemplate.execute(transactionStatus -> {
            CtSignRecord record = new CtSignRecord();
            record.setContractId(contract.getId());
            record.setSignTaskId(taskId);
            record.setSignerUserId(operatorId);
            record.setSealId(seal.getId());
            record.setCertSerial(artifact.certSerial());
            record.setSignedAt(artifact.signedAt());
            record.setOriginalSha256(artifact.originalSha256());
            record.setSignedSha256(artifact.signedSha256());
            record.setAlgorithm(artifact.algorithm());
            record.setEvidenceNo(artifact.evidenceNo());
            record.setChainHash(artifact.chainHash());
            record.setClientIp(clientIp);
            record.setRealNameStatus(REAL_NAME_UNVERIFIED);
            record.setRealNameMethod(METHOD_LOGIN_SESSION);
            record.setRealNameEvidenceNo("RN-" + taskId);
            record.setWillingnessMethod(METHOD_SIGN_CONFIRM);
            record.setWillingnessEvidenceNo("WL-" + taskId);
            record.setWillingnessAt(artifact.signedAt());
            signRecordMapper.insert(record);

            task.setStatus(TASK_SIGNED);
            task.setSignerUserId(operatorId);
            task.setSignedAt(artifact.signedAt());
            task.setSignedFileId(artifact.signedFileId());
            signTaskMapper.updateById(task);

            contract.setFileId(artifact.signedFileId());
            boolean allSigned = listTasks(contract.getId()).stream()
                    .filter(item -> !TASK_EXPIRED.equals(item.getStatus()))
                    .allMatch(item -> TASK_SIGNED.equals(item.getStatus()));
            contract.setStatus(allSigned ? CONTRACT_SIGNED : CONTRACT_SIGNING);
            contractMapper.updateById(contract);

            SignResultVO created = toResult(task, record, contract.getStatus());
            created.setStampedPages(artifact.stampedPages());
            return created;
        });
        log.info("合同签署完成: taskId={}, contractId={}, evidenceNo={}, pages={}",
                taskId, contract.getId(), artifact.evidenceNo(), artifact.stampedPages());
        return result;
    }

    /**
     * 准备签章件：加载底稿、视觉盖章、可选数字签名、上传文件并计算哈希链。
     *
     * @param contract  合同
     * @param task      签署任务
     * @param seal      印章
     * @param request   签署参数
     * @param operatorId 签署人
     * @return 签章件与证据要素
     */
    private PreparedArtifact prepareArtifact(CtContract contract, CtSignTask task, CtSeal seal,
                                             SignExecuteRequest request, Long operatorId) {
        Long versionId = task.getVersionId();
        byte[] pdf = loadBasePdf(contract, versionId);
        String originalSha256 = EsignHashUtils.sha256(pdf);
        byte[] sealImage = pdfStampService.loadSealImage(seal.getImageFileId());
        PdfStampService.StampResult stamped = pdfStampService.stamp(pdf, sealImage, request);
        SignatureService.SignatureOutcome signature = signatureService.sign(stamped.bytes());
        String signedSha256 = EsignHashUtils.sha256(signature.bytes());
        Long signedFileId = fileClient.upload(
                "contract-" + contract.getCode() + "-v" + contract.getCurrentVersion() + "-signed.pdf",
                "application/pdf", signature.bytes());
        LocalDateTime signedAt = LocalDateTime.now().withNano(0);
        String evidenceNo = "EV-" + task.getId();
        String chainHash = EsignHashUtils.chain(latestChain(contract.getId()), contract.getId(), versionId,
                operatorId, signedAt.toString(), originalSha256, signedSha256, evidenceNo);
        return new PreparedArtifact(originalSha256, signedSha256, signedFileId, stamped.stampedPages(),
                signedAt, evidenceNo, chainHash, signature.certSerial(), signature.algorithm());
    }

    /**
     * 签章件与证据要素。
     *
     * @param originalSha256 盖章前文件摘要
     * @param signedSha256   签章件摘要
     * @param signedFileId   签章件文件ID
     * @param stampedPages   实际盖章页数
     * @param signedAt       签署时间
     * @param evidenceNo     证据编号
     * @param chainHash      链式哈希
     * @param certSerial     证书序列号
     * @param algorithm      签名算法
     */
    private record PreparedArtifact(String originalSha256, String signedSha256, Long signedFileId,
                                    Integer stampedPages, LocalDateTime signedAt, String evidenceNo,
                                    String chainHash, String certSerial, String algorithm) {
    }

    @Override
    public void refuse(Long taskId, SignRefuseRequest request, OperatorContext operator) {
        CtSignTask task = requireTask(taskId);
        Long operatorId = requireOperatorId(operator);
        if (task.getSignerUserId() != null && !task.getSignerUserId().equals(operatorId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "biz.contract.service.signer-self-can-execute-refuse-signing", "仅签署人本人可执行拒签");
        }
        if (!TASK_PENDING.equals(task.getStatus()) && !"SIGNING".equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.contract.service.signing-task-status-cannot-refuse-signing", "签署任务状态不可拒签: {0}",
                          task.getStatus());
        }
        task.setStatus(TASK_REFUSED);
        task.setRefuseReason(request.getReason());
        signTaskMapper.updateById(task);
        log.info("合同签署已拒签: taskId={}, reason={}", taskId, request.getReason());
    }

    @Override
    public EvidenceVO evidence(Long contractId) {
        CtContract contract = requireContract(contractId);
        List<CtSignTask> tasks = listTasks(contractId);
        List<CtSignRecord> records = listRecords(contractId);
        EvidenceVO vo = new EvidenceVO();
        vo.setContractId(String.valueOf(contractId));
        vo.setContractCode(contract.getCode());
        vo.setContractStatus(contract.getStatus());
        vo.setTasks(tasks.stream()
                .sorted(Comparator.comparing(CtSignTask::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .map(task -> toTaskVo(task, contract)).toList());
        vo.setRecords(records.stream().map(SignServiceImpl::toRecordVo).toList());
        vo.setChainValid(validateChain(contractId, tasks, records));
        return vo;
    }

    @Override
    public VerifyResultVO verify(Long contractId) {
        CtContract contract = requireContract(contractId);
        List<CtSignTask> tasks = listTasks(contractId);
        List<CtSignRecord> records = listRecords(contractId);
        VerifyResultVO vo = new VerifyResultVO();
        vo.setContractId(String.valueOf(contractId));
        List<String> details = new ArrayList<>();
        vo.setDetails(details);
        if (records.isEmpty()) {
            vo.setHashMatch(false);
            vo.setChainValid(false);
            vo.setSignatureValid(false);
            details.add("尚无签署记录，无法验签");
            return vo;
        }
        CtSignRecord latest = records.get(records.size() - 1);
        CtSignTask latestTask = signTaskMapper.selectById(latest.getSignTaskId());
        Long fileId = latestTask != null && latestTask.getSignedFileId() != null
                ? latestTask.getSignedFileId() : contract.getFileId();
        vo.setSignedFileId(fileId == null ? null : String.valueOf(fileId));
        vo.setExpectedSha256(latest.getSignedSha256());
        vo.setAlgorithm(latest.getAlgorithm());
        boolean chainValid = validateChain(contractId, tasks, records);
        boolean hashMatch = false;
        SignatureService.VerifyOutcome cms = new SignatureService.VerifyOutcome(false, "签章件缺失");
        if (fileId == null) {
            details.add("合同缺少签章件文件ID");
        } else {
            byte[] bytes = fileClient.download(fileId);
            String actual = EsignHashUtils.sha256(bytes);
            vo.setActualSha256(actual);
            hashMatch = actual.equals(latest.getSignedSha256());
            details.add(hashMatch ? "文件哈希与签署记录一致" : "文件哈希与签署记录不一致（文件可能被篡改）");
            cms = signatureService.verify(bytes, latest.getCertSerial());
            details.add(cms.detail());
        }
        details.add(chainValid ? "证据链哈希连续" : "证据链哈希断裂（记录可能被篡改）");
        vo.setHashMatch(hashMatch);
        vo.setChainValid(chainValid);
        vo.setSignatureValid(hashMatch && chainValid && cms.valid());
        return vo;
    }

    @Override
    public void voidContract(Long contractId) {
        CtContract contract = requireContract(contractId);
        if (!CONTRACT_SIGNING.equals(contract.getStatus())
                && !CONTRACT_SIGNED.equals(contract.getStatus())
                && !CONTRACT_EFFECTIVE.equals(contract.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.contract.service.status-contract-allow-void", "合同当前状态不可作废: {0}", contract.getStatus());
        }
        contract.setStatus(CONTRACT_VOID);
        contractMapper.updateById(contract);
        for (CtSignTask task : listTasks(contractId)) {
            if (TASK_PENDING.equals(task.getStatus()) || "SIGNING".equals(task.getStatus())) {
                task.setStatus(TASK_EXPIRED);
                signTaskMapper.updateById(task);
            }
        }
        log.info("合同已作废: contractId={}", contractId);
    }

    /**
     * 解析签署任务来源：优先使用显式任务，否则按相对方签署人生成，最后回落合同负责人。
     *
     * @param contract 合同
     * @param request  发起请求
     * @return 任务规格列表
     */
    private List<SignTaskCreateRequest> resolveSpecs(CtContract contract, SignStartRequest request) {
        if (request != null && request.getTasks() != null && !request.getTasks().isEmpty()) {
            return request.getTasks();
        }
        List<CtContractParty> parties = partyMapper.selectList(new LambdaQueryWrapper<CtContractParty>()
                .eq(CtContractParty::getContractId, contract.getId())
                .orderByAsc(CtContractParty::getPartyType)
                .orderByAsc(CtContractParty::getId));
        List<SignTaskCreateRequest> specs = new ArrayList<>();
        for (CtContractParty party : parties) {
            if (party.getSignerUserId() == null) {
                continue;
            }
            SignTaskCreateRequest spec = new SignTaskCreateRequest();
            spec.setSignerUserId(party.getSignerUserId());
            spec.setSignerParty(party.getPartyType());
            spec.setSignerName(StringUtils.hasText(party.getSignerName()) ? party.getSignerName() : party.getName());
            specs.add(spec);
        }
        if (specs.isEmpty() && contract.getOwnerId() != null) {
            SignTaskCreateRequest spec = new SignTaskCreateRequest();
            spec.setSignerUserId(contract.getOwnerId());
            spec.setSignerParty("A");
            spec.setSignerName("合同负责人");
            specs.add(spec);
        }
        return specs;
    }

    /**
     * 判断既有任务是否已覆盖该签署人（幂等）。
     *
     * @param existing 既有任务
     * @param spec     任务规格
     * @return 已覆盖返回 {@code true}
     */
    private boolean alreadyCovered(List<CtSignTask> existing, SignTaskCreateRequest spec) {
        if (spec.getSignerUserId() == null) {
            return false;
        }
        return existing.stream().anyMatch(task -> spec.getSignerUserId().equals(task.getSignerUserId())
                && (spec.getSignerParty() == null || spec.getSignerParty().equals(task.getSignerParty()))
                && !TASK_EXPIRED.equals(task.getStatus()) && !TASK_REFUSED.equals(task.getStatus()));
    }

    /**
     * 校验印章可用性（状态 + 有效期）。
     *
     * @param seal 印章
     */
    private void assertSealUsable(CtSeal seal) {
        if (!STATUS_ENABLED.equals(seal.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "biz.contract.service.seal-disabled", "印章已停用: {0}", seal.getName());
        }
        LocalDate today = LocalDate.now();
        if (seal.getValidFrom() != null && today.isBefore(seal.getValidFrom())) {
            throw new BizException(ErrorCode.FORBIDDEN, "biz.contract.service.seal-reach-effective-date", "印章未到生效日期: {0}", seal.getName());
        }
        if (seal.getValidTo() != null && today.isAfter(seal.getValidTo())) {
            throw new BizException(ErrorCode.FORBIDDEN, "biz.contract.service.seal-past-validity", "印章已过有效期: {0}", seal.getName());
        }
    }

    /**
     * 校验用章授权（人 + 类别 + 金额上限 + 有效期）。
     *
     * @param seal        印章
     * @param contract    合同
     * @param granteeId   用章人
     */
    private void assertGranted(CtSeal seal, CtContract contract, Long granteeId) {
        List<CtSealGrant> grants = grantMapper.selectList(new LambdaQueryWrapper<CtSealGrant>()
                .eq(CtSealGrant::getSealId, seal.getId())
                .eq(CtSealGrant::getGranteeUserId, granteeId)
                .eq(CtSealGrant::getStatus, STATUS_ENABLED));
        LocalDate today = LocalDate.now();
        boolean matched = grants.stream().anyMatch(grant -> matchGrant(grant, contract, today));
        if (!matched) {
            throw new BizException(ErrorCode.FORBIDDEN, "biz.contract.service.seal-authorization-seal-cannot-used-contract",
                          "未获用章授权：印章[{0}] 不可由当前用户用于该合同", seal.getName());
        }
    }

    /**
     * 判断单条授权是否覆盖合同。
     *
     * @param grant    授权
     * @param contract 合同
     * @param today    当前日期
     * @return 覆盖返回 {@code true}
     */
    private boolean matchGrant(CtSealGrant grant, CtContract contract, LocalDate today) {
        if (StringUtils.hasText(grant.getContractCategory())
                && !grant.getContractCategory().equals(contract.getCategory())) {
            return false;
        }
        BigDecimal maxAmount = grant.getMaxAmount();
        if (maxAmount != null && contract.getAmount() != null
                && maxAmount.compareTo(contract.getAmount()) < 0) {
            return false;
        }
        if (grant.getValidFrom() != null && today.isBefore(grant.getValidFrom())) {
            return false;
        }
        return grant.getValidTo() == null || !today.isAfter(grant.getValidTo());
    }

    /**
     * 加载签署底稿 PDF：优先版本正文，其次合同正文，缺失时生成占位 PDF。
     *
     * @param contract  合同
     * @param versionId 版本ID
     * @return PDF 字节
     */
    private byte[] loadBasePdf(CtContract contract, Long versionId) {
        Long fileId = null;
        if (versionId != null) {
            CtContractVersion version = versionMapper.selectById(versionId);
            if (version != null) {
                fileId = version.getFileId();
            }
        }
        if (fileId == null) {
            fileId = contract.getFileId();
        }
        byte[] original = fileId == null ? null : fileClient.download(fileId);
        return pdfStampService.ensurePdf(original, contract.getCode(), contract.getTitle(), contract.getAmount());
    }

    /**
     * 查询合同当前版本的版本ID。
     *
     * @param contract 合同
     * @return 版本ID；不存在时返回 {@code null}
     */
    private Long currentVersionId(CtContract contract) {
        Integer versionNo = contract.getCurrentVersion() == null ? 1 : contract.getCurrentVersion();
        CtContractVersion version = versionMapper.selectOne(new LambdaQueryWrapper<CtContractVersion>()
                .eq(CtContractVersion::getContractId, contract.getId())
                .eq(CtContractVersion::getVersionNo, versionNo)
                .last("LIMIT 1"));
        return version == null ? null : version.getId();
    }

    /**
     * 查询合同最新一条链式哈希。
     *
     * @param contractId 合同ID
     * @return 链式哈希；无记录时返回空串
     */
    private String latestChain(Long contractId) {
        List<CtSignRecord> records = listRecords(contractId);
        return records.isEmpty() ? "" : records.get(records.size() - 1).getChainHash();
    }

    /**
     * 校验证据链连续性。
     *
     * @param contractId 合同ID
     * @param tasks      任务列表
     * @param records    记录列表（按签署时间升序）
     * @return 连续返回 {@code true}
     */
    private boolean validateChain(Long contractId, List<CtSignTask> tasks, List<CtSignRecord> records) {
        Map<Long, Long> versionByTask = new HashMap<>();
        for (CtSignTask task : tasks) {
            versionByTask.put(task.getId(), task.getVersionId());
        }
        String prev = "";
        for (CtSignRecord record : records) {
            String expected = EsignHashUtils.chain(prev, contractId, versionByTask.get(record.getSignTaskId()),
                    record.getSignerUserId(), record.getSignedAt().toString(),
                    record.getOriginalSha256(), record.getSignedSha256(), record.getEvidenceNo());
            if (!expected.equals(record.getChainHash())) {
                log.warn("证据链校验失败: contractId={}, recordId={}", contractId, record.getId());
                return false;
            }
            prev = record.getChainHash();
        }
        return true;
    }

    /**
     * 查询合同签署任务。
     *
     * @param contractId 合同ID
     * @return 任务列表
     */
    private List<CtSignTask> listTasks(Long contractId) {
        return signTaskMapper.selectList(new LambdaQueryWrapper<CtSignTask>()
                .eq(CtSignTask::getContractId, contractId)
                .orderByAsc(CtSignTask::getSeqNo)
                .orderByAsc(CtSignTask::getId));
    }

    /**
     * 查询合同签署记录（按签署时间升序）。
     *
     * @param contractId 合同ID
     * @return 记录列表
     */
    private List<CtSignRecord> listRecords(Long contractId) {
        return signRecordMapper.selectList(new LambdaQueryWrapper<CtSignRecord>()
                .eq(CtSignRecord::getContractId, contractId)
                .orderByAsc(CtSignRecord::getSignedAt)
                .orderByAsc(CtSignRecord::getId));
    }

    /**
     * 校验合同存在。
     *
     * @param id 合同ID
     * @return 合同实体
     */
    private CtContract requireContract(Long id) {
        CtContract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.contract.service.contract-found", "合同不存在: {0}", id);
        }
        return contract;
    }

    /**
     * 校验签署任务存在。
     *
     * @param id 任务ID
     * @return 任务实体
     */
    private CtSignTask requireTask(Long id) {
        CtSignTask task = signTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.contract.service.signing-task-found", "签署任务不存在: {0}", id);
        }
        return task;
    }

    /**
     * 解析并校验当前操作人。
     *
     * @param operator 操作人上下文
     * @return 用户ID
     */
    private Long requireOperatorId(OperatorContext operator) {
        Long userId = parseUserId(operator);
        if (userId == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "biz.contract.service.cannot-identify-operator", "无法识别当前操作人");
        }
        return userId;
    }

    /**
     * 解析用户ID文本。
     *
     * @param operator 操作人上下文
     * @return 用户ID；非法时返回 {@code null}
     */
    private Long parseUserId(OperatorContext operator) {
        if (operator == null || !StringUtils.hasText(operator.userId())) {
            return null;
        }
        try {
            return Long.valueOf(operator.userId());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 任务实体转视图。
     *
     * @param task     任务实体
     * @param contract 合同
     * @return 视图对象
     */
    private SignTaskVO toTaskVo(CtSignTask task, CtContract contract) {
        SignTaskVO vo = new SignTaskVO();
        vo.setId(String.valueOf(task.getId()));
        vo.setContractId(String.valueOf(task.getContractId()));
        vo.setVersionId(task.getVersionId() == null ? null : String.valueOf(task.getVersionId()));
        vo.setSeqNo(task.getSeqNo());
        vo.setSignerUserId(task.getSignerUserId() == null ? null : String.valueOf(task.getSignerUserId()));
        vo.setSignerParty(task.getSignerParty());
        vo.setSignerName(task.getSignerName());
        vo.setProvider(task.getProvider());
        vo.setProviderTaskId(task.getProviderTaskId());
        vo.setSignUrl(task.getSignUrl());
        vo.setWorkflowTaskId(task.getWorkflowTaskId() == null ? null : String.valueOf(task.getWorkflowTaskId()));
        vo.setStatus(task.getStatus());
        vo.setSignedAt(task.getSignedAt());
        vo.setSignedFileId(task.getSignedFileId() == null ? null : String.valueOf(task.getSignedFileId()));
        vo.setRefuseReason(task.getRefuseReason());
        vo.setCreateTime(task.getCreateTime());
        if (contract != null) {
            vo.setContractCode(contract.getCode());
            vo.setContractTitle(contract.getTitle());
            vo.setContractAmount(contract.getAmount());
            vo.setContractCategory(contract.getCategory());
        }
        return vo;
    }

    /**
     * 记录实体转视图。
     *
     * @param record 记录实体
     * @return 视图对象
     */
    private static SignRecordVO toRecordVo(CtSignRecord record) {
        SignRecordVO vo = new SignRecordVO();
        vo.setId(String.valueOf(record.getId()));
        vo.setContractId(String.valueOf(record.getContractId()));
        vo.setSignTaskId(String.valueOf(record.getSignTaskId()));
        vo.setSignerUserId(record.getSignerUserId() == null ? null : String.valueOf(record.getSignerUserId()));
        vo.setSealId(record.getSealId() == null ? null : String.valueOf(record.getSealId()));
        vo.setCertSerial(record.getCertSerial());
        vo.setSignedAt(record.getSignedAt());
        vo.setOriginalSha256(record.getOriginalSha256());
        vo.setSignedSha256(record.getSignedSha256());
        vo.setAlgorithm(record.getAlgorithm());
        vo.setEvidenceNo(record.getEvidenceNo());
        vo.setChainHash(record.getChainHash());
        vo.setClientIp(record.getClientIp());
        vo.setRealNameStatus(record.getRealNameStatus());
        vo.setRealNameMethod(record.getRealNameMethod());
        vo.setRealNameEvidenceNo(record.getRealNameEvidenceNo());
        vo.setWillingnessMethod(record.getWillingnessMethod());
        vo.setWillingnessEvidenceNo(record.getWillingnessEvidenceNo());
        vo.setWillingnessAt(record.getWillingnessAt());
        return vo;
    }

    /**
     * 组装签署结果。
     *
     * @param task           任务
     * @param record         记录
     * @param contractStatus 合同状态
     * @return 签署结果
     */
    private SignResultVO toResult(CtSignTask task, CtSignRecord record, String contractStatus) {
        SignResultVO result = new SignResultVO();
        result.setTaskId(String.valueOf(task.getId()));
        result.setRecordId(String.valueOf(record.getId()));
        result.setEvidenceNo(record.getEvidenceNo());
        result.setOriginalSha256(record.getOriginalSha256());
        result.setSignedSha256(record.getSignedSha256());
        result.setAlgorithm(record.getAlgorithm());
        result.setSignedFileId(task.getSignedFileId() == null ? null : String.valueOf(task.getSignedFileId()));
        result.setTaskStatus(task.getStatus());
        result.setContractStatus(contractStatus);
        return result;
    }
}
