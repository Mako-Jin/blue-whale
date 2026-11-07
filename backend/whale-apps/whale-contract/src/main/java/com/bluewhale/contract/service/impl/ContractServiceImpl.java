package com.bluewhale.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.contract.converter.ContractConverter;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.contract.client.ArchiveInternalClient;
import com.bluewhale.contract.client.ContractApprovalClient;
import com.bluewhale.contract.config.ContractApprovalProperties;
import com.bluewhale.contract.config.ContractArchiveProperties;
import com.bluewhale.contract.domain.ContractStatus;
import com.bluewhale.contract.dto.ContractPartyRequest;
import com.bluewhale.contract.dto.ContractQueryRequest;
import com.bluewhale.contract.dto.ContractSaveRequest;
import com.bluewhale.contract.dto.ContractVersionSaveRequest;
import com.bluewhale.contract.dto.OperatorContext;
import com.bluewhale.contract.entity.CtContract;
import com.bluewhale.contract.entity.CtContractFlow;
import com.bluewhale.contract.entity.CtContractParty;
import com.bluewhale.contract.entity.CtContractVersion;
import com.bluewhale.contract.mapper.CtContractFlowMapper;
import com.bluewhale.contract.mapper.CtContractMapper;
import com.bluewhale.contract.mapper.CtContractPartyMapper;
import com.bluewhale.contract.mapper.CtContractVersionMapper;
import com.bluewhale.contract.service.ContractService;
import com.bluewhale.contract.vo.ContractFlowVO;
import com.bluewhale.contract.vo.ContractPartyVO;
import com.bluewhale.contract.vo.ContractVO;
import com.bluewhale.contract.vo.ContractVersionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 合同主流程服务实现。
 *
 * <p>状态流转只由本服务驱动，前端不可直接改状态；审批结果通过工作流实例状态回读同步。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 合同编号流水位数 */
    private static final int CODE_SEQ_WIDTH = 6;

    /** 到期提醒阈值（天） */
    private static final long EXPIRING_DAYS = 30L;

    /** 类别码映射（编号用） */
    private static final Map<String, String> CATEGORY_CODES = Map.of(
            "PURCHASE", "CG",
            "SALES", "XS",
            "SERVICE", "FW",
            "LEASE", "ZL",
            "OTHER", "QT");

    /** 版本状态：草稿 */
    private static final String VERSION_DRAFT = "DRAFT";

    /** 版本状态：被替代 */
    private static final String VERSION_SUPERSEDED = "SUPERSEDED";

    /** 流程阶段：审批 */
    private static final String STAGE_APPROVAL = "APPROVAL";

    /** 流程状态：运行中 */
    private static final String FLOW_RUNNING = "RUNNING";

    /** 流程状态：已通过 */
    private static final String FLOW_APPROVED = "APPROVED";

    /** 流程状态：已驳回 */
    private static final String FLOW_REJECTED = "REJECTED";

    /** 流程状态：已撤销 */
    private static final String FLOW_CANCELLED = "CANCELLED";

    /** 工作流实例状态：已通过 */
    private static final int INSTANCE_APPROVED = 1;

    /** 工作流实例状态：已驳回 */
    private static final int INSTANCE_REJECTED = 2;

    /** 工作流实例状态：已撤销 */
    private static final int INSTANCE_CANCELLED = 3;

    private final CtContractMapper contractMapper;

    private final CtContractPartyMapper partyMapper;

    private final CtContractVersionMapper versionMapper;

    private final CtContractFlowMapper flowMapper;

    private final ContractApprovalClient approvalClient;

    private final ContractApprovalProperties approvalProperties;

    private final ArchiveInternalClient archiveClient;

    private final ContractArchiveProperties archiveProperties;

    @Override
    public PageResult<ContractVO> page(ContractQueryRequest request) {
        LambdaQueryWrapper<CtContract> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getCode()), CtContract::getCode, request.getCode());
        wrapper.like(StringUtils.hasText(request.getTitle()), CtContract::getTitle, request.getTitle());
        wrapper.eq(StringUtils.hasText(request.getCategory()), CtContract::getCategory, request.getCategory());
        wrapper.eq(StringUtils.hasText(request.getStatus()), CtContract::getStatus, request.getStatus());
        wrapper.ge(request.getAmountMin() != null, CtContract::getAmount, request.getAmountMin());
        wrapper.le(request.getAmountMax() != null, CtContract::getAmount, request.getAmountMax());
        wrapper.eq(StringUtils.hasText(request.getBizType()), CtContract::getBizType, request.getBizType());
        wrapper.eq(StringUtils.hasText(request.getBizId()), CtContract::getBizId, request.getBizId());
        if (Boolean.TRUE.equals(request.getExpiringSoon())) {
            LocalDate today = LocalDate.now();
            wrapper.in(CtContract::getStatus, List.of(
                    ContractStatus.APPROVED.name(), ContractStatus.SIGNING.name(),
                    ContractStatus.SIGNED.name(), ContractStatus.EFFECTIVE.name()));
            wrapper.between(CtContract::getExpireDate, today, today.plusDays(EXPIRING_DAYS));
        }
        wrapper.orderByDesc(CtContract::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        long current = Math.max(request.getCurrent(), 1L);
        Page<CtContract> page = contractMapper.selectPage(new Page<>(current, size), wrapper);
        List<ContractVO> records = page.getRecords().stream()
                .map(ContractServiceImpl::toVo)
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<ContractVO> listByBiz(String bizType, String bizId) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "bizType 与 bizId 不能为空");
        }
        LambdaQueryWrapper<CtContract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CtContract::getBizType, bizType.trim());
        wrapper.eq(CtContract::getBizId, bizId.trim());
        wrapper.orderByDesc(CtContract::getCreateTime);
        return contractMapper.selectList(wrapper).stream()
                .map(ContractServiceImpl::toVo)
                .toList();
    }

    @Override
    public ContractVO getDetail(Long id) {
        CtContract contract = requireContract(id);
        if (ContractStatus.APPROVING.name().equals(contract.getStatus())) {
            syncApprovalQuietly(contract);
        }
        ContractVO vo = toVo(contract);
        vo.setParties(partyMapper.selectList(new LambdaQueryWrapper<CtContractParty>()
                        .eq(CtContractParty::getContractId, id)
                        .orderByAsc(CtContractParty::getPartyType)
                        .orderByAsc(CtContractParty::getId))
                .stream().map(ContractServiceImpl::toPartyVo).toList());
        vo.setVersions(versionMapper.selectList(new LambdaQueryWrapper<CtContractVersion>()
                        .eq(CtContractVersion::getContractId, id)
                        .orderByDesc(CtContractVersion::getVersionNo))
                .stream().map(ContractServiceImpl::toVersionVo).toList());
        vo.setFlows(flowMapper.selectList(new LambdaQueryWrapper<CtContractFlow>()
                        .eq(CtContractFlow::getContractId, id)
                        .orderByDesc(CtContractFlow::getCreateTime))
                .stream().map(ContractServiceImpl::toFlowVo).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVO create(ContractSaveRequest request, OperatorContext operator) {
        assertDateRange(request.getEffectiveDate(), request.getExpireDate());
        CtContract contract = new CtContract();
        applyRequest(request, contract);
        contract.setCode(nextCode(request.getCategory()));
        contract.setStatus(ContractStatus.DRAFT.name());
        contract.setCurrentVersion(1);
        contract.setCurrency(StringUtils.hasText(request.getCurrency()) ? request.getCurrency() : "CNY");
        contract.setOwnerId(request.getOwnerId() == null ? parseUserId(operator) : request.getOwnerId());
        contractMapper.insert(contract);
        insertParties(contract.getId(), request.getParties());
        insertVersion(contract.getId(), 1, request.getFileId(), null, "初始版本");
        log.info("合同已创建: id={}, code={}", contract.getId(), contract.getCode());
        return toVo(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVO update(Long id, ContractSaveRequest request) {
        CtContract contract = requireContract(id);
        requireStatus(contract, "编辑", ContractStatus.DRAFT);
        assertDateRange(request.getEffectiveDate(), request.getExpireDate());
        applyRequest(request, contract);
        if (StringUtils.hasText(request.getCurrency())) {
            contract.setCurrency(request.getCurrency());
        }
        contractMapper.updateById(contract);
        partyMapper.delete(new LambdaQueryWrapper<CtContractParty>().eq(CtContractParty::getContractId, id));
        insertParties(id, request.getParties());
        log.info("合同已更新: id={}, code={}", id, contract.getCode());
        return toVo(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CtContract contract = requireContract(id);
        requireStatus(contract, "删除", ContractStatus.DRAFT);
        partyMapper.delete(new LambdaQueryWrapper<CtContractParty>().eq(CtContractParty::getContractId, id));
        contractMapper.deleteById(id);
        log.info("合同已删除: id={}, code={}", id, contract.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVO submit(Long id, OperatorContext operator) {
        CtContract contract = requireContract(id);
        requireStatus(contract, "提交审批", ContractStatus.DRAFT, ContractStatus.REJECTED, ContractStatus.WITHDRAWN);
        if (!approvalProperties.isEnabled() || approvalProperties.getDefinitionId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "合同审批流程未配置，请设置 whale.contract.approval.definition-id");
        }
        String businessKey = businessKey(contract);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("contractId", String.valueOf(contract.getId()));
        variables.put("code", contract.getCode());
        variables.put("version", contract.getCurrentVersion());
        variables.put("category", contract.getCategory());
        variables.put("amount", contract.getAmount() == null ? BigDecimal.ZERO : contract.getAmount());
        Long instanceId = approvalClient.start(approvalProperties.getDefinitionId(), contract.getTitle(),
                businessKey, variables, operator);
        cancelRunningFlows(contract.getId(), operator);
        CtContractFlow flow = new CtContractFlow();
        flow.setContractId(contract.getId());
        flow.setVersionId(currentVersionId(contract));
        flow.setProcessInstanceId(instanceId);
        flow.setDefinitionId(approvalProperties.getDefinitionId());
        flow.setStage(STAGE_APPROVAL);
        flow.setStatus(FLOW_RUNNING);
        flowMapper.insert(flow);
        contract.setStatus(ContractStatus.APPROVING.name());
        contractMapper.updateById(contract);
        log.info("合同已提交审批: id={}, businessKey={}, instanceId={}", id, businessKey, instanceId);
        return toVo(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVO withdraw(Long id, OperatorContext operator) {
        CtContract contract = requireContract(id);
        requireStatus(contract, "撤回", ContractStatus.APPROVING);
        cancelRunningFlows(contract.getId(), operator);
        contract.setStatus(ContractStatus.WITHDRAWN.name());
        contractMapper.updateById(contract);
        log.info("合同已撤回: id={}", id);
        return toVo(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVO terminate(Long id) {
        CtContract contract = requireContract(id);
        requireStatus(contract, "终止", ContractStatus.APPROVED, ContractStatus.SIGNING,
                ContractStatus.SIGNED, ContractStatus.EFFECTIVE);
        contract.setStatus(ContractStatus.TERMINATED.name());
        contractMapper.updateById(contract);
        log.info("合同已终止: id={}", id);
        return toVo(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVO effective(Long id) {
        CtContract contract = requireContract(id);
        requireStatus(contract, "生效", ContractStatus.SIGNED);
        contract.setStatus(ContractStatus.EFFECTIVE.name());
        contractMapper.updateById(contract);
        log.info("合同已生效: id={}", id);
        return toVo(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVO archive(Long id) {
        CtContract contract = requireContract(id);
        requireStatus(contract, "归档", ContractStatus.EFFECTIVE);
        contract.setStatus(ContractStatus.ARCHIVED.name());
        if (archiveProperties.isEnabled()) {
            List<CtContractParty> parties = partyMapper.selectList(new LambdaQueryWrapper<CtContractParty>()
                    .eq(CtContractParty::getContractId, id));
            Long archiveItemId = archiveClient.ingest(contract, parties);
            contract.setArchiveItemId(archiveItemId);
            log.info("合同快照已入档: id={}, archiveItemId={}", id, archiveItemId);
        }
        contractMapper.updateById(contract);
        log.info("合同已归档: id={}", id);
        return toVo(contract);
    }

    @Override
    public List<ContractVersionVO> listVersions(Long id) {
        requireContract(id);
        return versionMapper.selectList(new LambdaQueryWrapper<CtContractVersion>()
                        .eq(CtContractVersion::getContractId, id)
                        .orderByDesc(CtContractVersion::getVersionNo))
                .stream().map(ContractServiceImpl::toVersionVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVersionVO createVersion(Long id, ContractVersionSaveRequest request) {
        CtContract contract = requireContract(id);
        requireStatus(contract, "修订", ContractStatus.DRAFT, ContractStatus.REJECTED, ContractStatus.WITHDRAWN);
        versionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CtContractVersion>()
                .eq(CtContractVersion::getContractId, id)
                .ne(CtContractVersion::getStatus, VERSION_SUPERSEDED)
                .set(CtContractVersion::getStatus, VERSION_SUPERSEDED));
        int nextVersion = (contract.getCurrentVersion() == null ? 0 : contract.getCurrentVersion()) + 1;
        Long fileId = request.getFileId() == null ? contract.getFileId() : request.getFileId();
        CtContractVersion version = insertVersion(id, nextVersion, fileId, request.getSha256(),
                request.getChangeNote() == null ? "修订" : request.getChangeNote());
        contract.setCurrentVersion(nextVersion);
        contract.setStatus(ContractStatus.DRAFT.name());
        if (request.getFileId() != null) {
            contract.setFileId(request.getFileId());
        }
        contractMapper.updateById(contract);
        log.info("合同已创建新版本: id={}, version={}", id, nextVersion);
        return toVersionVo(version);
    }

    /**
     * 回读工作流实例状态并推进合同状态（失败仅告警，不影响详情读取）。
     *
     * @param contract 合同实体
     */
    private void syncApprovalQuietly(CtContract contract) {
        List<CtContractFlow> flows = flowMapper.selectList(new LambdaQueryWrapper<CtContractFlow>()
                .eq(CtContractFlow::getContractId, contract.getId())
                .eq(CtContractFlow::getStage, STAGE_APPROVAL)
                .isNotNull(CtContractFlow::getProcessInstanceId)
                .orderByDesc(CtContractFlow::getCreateTime));
        if (flows.isEmpty()) {
            return;
        }
        CtContractFlow flow = flows.get(0);
        try {
            Integer instanceStatus = approvalClient.fetchStatus(flow.getProcessInstanceId());
            if (instanceStatus == null || instanceStatus == 0) {
                return;
            }
            if (instanceStatus == INSTANCE_APPROVED) {
                flow.setStatus(FLOW_APPROVED);
                contract.setStatus(ContractStatus.APPROVED.name());
            } else if (instanceStatus == INSTANCE_REJECTED) {
                flow.setStatus(FLOW_REJECTED);
                contract.setStatus(ContractStatus.REJECTED.name());
            } else if (instanceStatus == INSTANCE_CANCELLED) {
                flow.setStatus(FLOW_CANCELLED);
                contract.setStatus(ContractStatus.WITHDRAWN.name());
            }
            flowMapper.updateById(flow);
            contractMapper.updateById(contract);
            log.info("合同审批结果已同步: id={}, instance={}, status={}",
                    contract.getId(), flow.getProcessInstanceId(), contract.getStatus());
        } catch (RuntimeException ex) {
            log.warn("合同审批结果同步失败: id={}, instance={}, reason={}",
                    contract.getId(), flow.getProcessInstanceId(), ex.getMessage());
        }
    }

    /**
     * 撤销该合同下仍在运行中的流程实例（尽力而为）。
     *
     * @param contractId 合同ID
     * @param operator   当前操作人
     */
    private void cancelRunningFlows(Long contractId, OperatorContext operator) {
        List<CtContractFlow> running = flowMapper.selectList(new LambdaQueryWrapper<CtContractFlow>()
                .eq(CtContractFlow::getContractId, contractId)
                .eq(CtContractFlow::getStatus, FLOW_RUNNING));
        for (CtContractFlow flow : running) {
            if (flow.getProcessInstanceId() != null) {
                try {
                    approvalClient.cancel(flow.getProcessInstanceId(), operator);
                } catch (RuntimeException ex) {
                    log.warn("撤销流程实例失败（继续本地撤回）: instance={}, reason={}",
                            flow.getProcessInstanceId(), ex.getMessage());
                }
            }
            flow.setStatus(FLOW_CANCELLED);
            flowMapper.updateById(flow);
        }
    }

    /**
     * 当前版本记录ID。
     *
     * @param contract 合同实体
     * @return 版本记录ID，缺失返回 {@code null}
     */
    private Long currentVersionId(CtContract contract) {
        List<CtContractVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<CtContractVersion>()
                .eq(CtContractVersion::getContractId, contract.getId())
                .eq(CtContractVersion::getVersionNo, contract.getCurrentVersion()));
        return versions.isEmpty() ? null : versions.get(0).getId();
    }

    /**
     * 生成合同编号 HT-{类别码}-{yyyy}-{6位流水}。
     *
     * @param category 合同类别
     * @return 租户内唯一编号
     */
    private String nextCode(String category) {
        String categoryCode = CATEGORY_CODES.getOrDefault(category, "QT");
        String prefix = "HT-" + categoryCode + "-"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy")) + "-";
        long seq = parseSeq(contractMapper.selectMaxCodeByPrefix(prefix)) + 1;
        for (int i = 0; i < 1000; i++, seq++) {
            String code = prefix + String.format("%0" + CODE_SEQ_WIDTH + "d", seq);
            if (contractMapper.countByCodeIncludingDeleted(code) == 0) {
                return code;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "合同编号生成失败，请稍后重试");
    }

    /**
     * 解析编号末段的流水号。
     *
     * @param code 合同编号
     * @return 流水号，编号为空或格式非法返回 0
     */
    private static long parseSeq(String code) {
        if (!StringUtils.hasText(code)) {
            return 0L;
        }
        int dash = code.lastIndexOf('-');
        if (dash < 0 || dash == code.length() - 1) {
            return 0L;
        }
        try {
            return Long.parseLong(code.substring(dash + 1));
        } catch (NumberFormatException ex) {
            log.warn("合同编号流水段非法，忽略: {}", code);
            return 0L;
        }
    }

    /**
     * 把请求字段写入实体（不含编号、状态、版本）。
     *
     * @param request  请求
     * @param contract 实体
     */
    private void applyRequest(ContractSaveRequest request, CtContract contract) {
        contract.setTitle(request.getTitle().trim());
        contract.setCategory(request.getCategory().trim());
        contract.setAmount(request.getAmount());
        contract.setOwnerId(request.getOwnerId() == null ? contract.getOwnerId() : request.getOwnerId());
        contract.setDeptId(request.getDeptId());
        contract.setFileId(request.getFileId() == null ? contract.getFileId() : request.getFileId());
        contract.setEffectiveDate(request.getEffectiveDate());
        contract.setExpireDate(request.getExpireDate());
        contract.setBizType(request.getBizType());
        contract.setBizId(request.getBizId());
        contract.setRemark(request.getRemark());
    }

    /**
     * 插入相对方列表。
     *
     * @param contractId 合同ID
     * @param parties    相对方请求列表
     */
    private void insertParties(Long contractId, List<ContractPartyRequest> parties) {
        if (parties == null) {
            return;
        }
        for (ContractPartyRequest request : parties) {
            CtContractParty party = new CtContractParty();
            party.setContractId(contractId);
            party.setPartyType(request.getPartyType());
            party.setName(request.getName());
            party.setCreditCode(request.getCreditCode());
            party.setContact(request.getContact());
            party.setPhone(request.getPhone());
            party.setSignerUserId(request.getSignerUserId());
            party.setSignerName(request.getSignerName());
            partyMapper.insert(party);
        }
    }

    /**
     * 插入合同版本。
     *
     * @param contractId 合同ID
     * @param versionNo  版本号
     * @param fileId     文件ID
     * @param sha256     文件哈希
     * @param changeNote 变更说明
     * @return 版本实体
     */
    private CtContractVersion insertVersion(Long contractId, int versionNo, Long fileId, String sha256,
                                            String changeNote) {
        CtContractVersion version = new CtContractVersion();
        version.setContractId(contractId);
        version.setVersionNo(versionNo);
        version.setFileId(fileId);
        version.setSha256(sha256);
        version.setChangeNote(changeNote);
        version.setStatus(VERSION_DRAFT);
        versionMapper.insert(version);
        return version;
    }

    /**
     * 按 ID 查询合同，不存在时抛 404。
     *
     * @param id 合同ID
     * @return 合同实体
     */
    private CtContract requireContract(Long id) {
        CtContract contract = id == null ? null : contractMapper.selectById(id);
        if (contract == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "合同不存在");
        }
        return contract;
    }

    /**
     * 校验合同状态是否允许当前操作。
     *
     * @param contract 合同实体
     * @param action   操作名（用于提示）
     * @param allowed  允许的状态集合
     */
    private void requireStatus(CtContract contract, String action, ContractStatus... allowed) {
        for (ContractStatus status : allowed) {
            if (status.name().equals(contract.getStatus())) {
                return;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR,
                "当前状态（" + contract.getStatus() + "）不允许" + action + "合同");
    }

    /**
     * 校验生效/到期日期区间。
     *
     * @param effectiveDate 生效日期
     * @param expireDate    到期日期
     */
    private void assertDateRange(LocalDate effectiveDate, LocalDate expireDate) {
        if (effectiveDate != null && expireDate != null && expireDate.isBefore(effectiveDate)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "到期日期不得早于生效日期");
        }
    }

    /**
     * 解析操作人用户ID。
     *
     * @param operator 操作人上下文
     * @return 用户ID；无法解析返回 {@code null}
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
     * 构造工作流业务键。
     *
     * @param contract 合同实体
     * @return {@code contract:{id}:v{version}}
     */
    private String businessKey(CtContract contract) {
        return "contract:" + contract.getId() + ":v" + contract.getCurrentVersion();
    }

    /**
     * 实体转视图对象。
     *
     * @param contract 合同实体
     * @return 视图对象
     */
    private static ContractVO toVo(CtContract contract) {
        ContractVO vo = new ContractVO();
        vo.setId(Objects.toString(contract.getId(), null));
        vo.setCode(contract.getCode());
        vo.setTitle(contract.getTitle());
        vo.setCategory(contract.getCategory());
        vo.setAmount(contract.getAmount());
        vo.setCurrency(contract.getCurrency());
        vo.setOwnerId(Objects.toString(contract.getOwnerId(), null));
        vo.setDeptId(Objects.toString(contract.getDeptId(), null));
        vo.setStatus(contract.getStatus());
        vo.setCurrentVersion(contract.getCurrentVersion());
        vo.setFileId(Objects.toString(contract.getFileId(), null));
        vo.setEffectiveDate(contract.getEffectiveDate());
        vo.setExpireDate(contract.getExpireDate());
        vo.setDaysToExpire(contract.getExpireDate() == null ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), contract.getExpireDate()));
        vo.setArchiveItemId(Objects.toString(contract.getArchiveItemId(), null));
        vo.setBizType(contract.getBizType());
        vo.setBizId(contract.getBizId());
        vo.setRemark(contract.getRemark());
        vo.setCreateTime(contract.getCreateTime());
        vo.setUpdateTime(contract.getUpdateTime());
        return vo;
    }

    /**
     * 相对方实体转视图对象。
     *
     * @param party 实体
     * @return 视图对象
     */
    private static ContractPartyVO toPartyVo(CtContractParty party) {
        ContractPartyVO vo = ContractConverter.INSTANCE.toPartyVo(party);
        vo.setId(Objects.toString(party.getId(), null));
        vo.setContractId(Objects.toString(party.getContractId(), null));
        vo.setSignerUserId(Objects.toString(party.getSignerUserId(), null));
        return vo;
    }

    /**
     * 版本实体转视图对象。
     *
     * @param version 实体
     * @return 视图对象
     */
    private static ContractVersionVO toVersionVo(CtContractVersion version) {
        ContractVersionVO vo = ContractConverter.INSTANCE.toVersionVo(version);
        vo.setId(Objects.toString(version.getId(), null));
        vo.setContractId(Objects.toString(version.getContractId(), null));
        vo.setFileId(Objects.toString(version.getFileId(), null));
        return vo;
    }

    /**
     * 流程记录实体转视图对象。
     *
     * @param flow 实体
     * @return 视图对象
     */
    private static ContractFlowVO toFlowVo(CtContractFlow flow) {
        ContractFlowVO vo = ContractConverter.INSTANCE.toFlowVo(flow);
        vo.setId(Objects.toString(flow.getId(), null));
        vo.setContractId(Objects.toString(flow.getContractId(), null));
        vo.setVersionId(Objects.toString(flow.getVersionId(), null));
        vo.setProcessInstanceId(Objects.toString(flow.getProcessInstanceId(), null));
        vo.setDefinitionId(Objects.toString(flow.getDefinitionId(), null));
        return vo;
    }
}
