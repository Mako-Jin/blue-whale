package com.bluewhale.oa.administrative.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.administrative.application.command.AdministrativeCommands;
import com.bluewhale.oa.administrative.application.service.SealUseAppService;
import com.bluewhale.oa.administrative.application.view.AdministrativeViews;
import com.bluewhale.oa.administrative.domain.gateway.ContractSealGateway;
import com.bluewhale.oa.administrative.domain.model.AdministrativeQuery;
import com.bluewhale.oa.administrative.domain.model.SealUse;
import com.bluewhale.oa.administrative.domain.model.SealUseFile;
import com.bluewhale.oa.administrative.domain.model.SealUseStatus;
import com.bluewhale.oa.administrative.domain.repository.AdministrativeRepository;
import com.bluewhale.oa.approval.application.command.ApprovalInstanceRegisterCommand;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceView;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.SequenceAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用章应用服务实现。
 *
 * <p>申请前通过 Contract 内网接口校验印章授权；审批登记仅允许一次。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SealUseAppServiceImpl implements SealUseAppService {

    private static final String BIZ_TYPE_SEAL = "SEAL_USE";

    private static final String CODE_BIZ_TYPE = "SEAL_USE";

    private static final String CODE_PREFIX = "SEAL";

    private final AdministrativeRepository repository;

    private final ApprovalInstanceAppService approvalInstanceAppService;

    private final SequenceAppService sequenceAppService;

    private final ContractSealGateway contractSealGateway;

    @Override
    public PageResult<AdministrativeViews.SealUseView> page(AdministrativeQuery query) {
        List<AdministrativeViews.SealUseView> records = repository.pageSealUses(query).stream()
                .map(this::toView).toList();
        return PageResult.of(records, repository.countSealUses(query), query.getCurrent(), query.getSize());
    }

    @Override
    public AdministrativeViews.SealUseView detail(Long id) {
        return toView(requireSealUse(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.SealUseView create(AdministrativeCommands.SealUseSaveCommand command) {
        assertGranted(command.getSealId(), command.getApplicantId());
        SealUse sealUse = new SealUse();
        apply(sealUse, command);
        sealUse.setCode(sequenceAppService.nextCode(CODE_BIZ_TYPE, CODE_PREFIX));
        sealUse.setStatus(SealUseStatus.PENDING.name());
        sealUse.setFiles(buildFiles(command.getFiles()));
        sealUse.setFileCount(sealUse.getFiles().size());
        repository.insertSealUse(sealUse);
        repository.replaceSealUseFiles(sealUse.getId(), sealUse.getFiles());
        registerApproval(sealUse);
        log.info("用章申请已创建: id={}, code={}, sealId={}", sealUse.getId(), sealUse.getCode(),
                sealUse.getSealId());
        return toView(sealUse);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.SealUseView update(Long id, AdministrativeCommands.SealUseSaveCommand command) {
        SealUse sealUse = requireSealUse(id);
        sealUse.assertEditable();
        assertGranted(command.getSealId(), command.getApplicantId());
        apply(sealUse, command);
        sealUse.setFiles(buildFiles(command.getFiles()));
        sealUse.setFileCount(sealUse.getFiles().size());
        repository.updateSealUse(sealUse);
        repository.replaceSealUseFiles(id, sealUse.getFiles());
        return toView(sealUse);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SealUse sealUse = requireSealUse(id);
        sealUse.assertEditable();
        repository.deleteSealUse(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, AdministrativeCommands.SealUseStatusCommand command) {
        SealUse sealUse = requireSealUse(id);
        if (!sealUse.changeStatus(SealUseStatus.of(command.getStatus()))) {
            return;
        }
        repository.updateSealUse(sealUse);
        log.info("用章状态已变更: id={}, status={}", id, sealUse.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.SealUseView register(Long id, Long operatorId) {
        SealUse sealUse = requireSealUse(id);
        sealUse.register(operatorId);
        repository.updateSealUse(sealUse);
        log.info("用章已登记: id={}, operatorId={}", id, operatorId);
        return toView(sealUse);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncApprovalResult(Long sealUseId, String result) {
        if (SealUseStatus.of(result) == SealUseStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.SEAL_STATUS_CONFLICT, "审批结果不能为待审批");
        }
        AdministrativeCommands.SealUseStatusCommand command = new AdministrativeCommands.SealUseStatusCommand();
        command.setStatus(result);
        updateStatus(sealUseId, command);
    }

    @Override
    public List<ContractSealGateway.SealInfo> availableSeals(Long userId) {
        return contractSealGateway.grantedSeals(userId);
    }

    /**
     * 校验印章与申请人非空且申请人已被授予该印章权限。
     *
     * @param sealId 印章 ID
     * @param applicantId 申请人 ID
     */
    private void assertGranted(Long sealId, Long applicantId) {
        if (sealId == null || applicantId == null) {
            throw OaErrorCode.exception(OaErrorCode.SEAL_INVALID, "印章与申请人不能为空");
        }
        if (!contractSealGateway.isGranted(sealId, applicantId)) {
            throw OaErrorCode.exception(OaErrorCode.SEAL_NOT_AUTHORIZED);
        }
    }

    /**
     * 将保存命令中的字段应用到用章申请。
     *
     * @param sealUse 用章申请
     * @param command 用章申请保存命令
     */
    private void apply(SealUse sealUse, AdministrativeCommands.SealUseSaveCommand command) {
        sealUse.setApplicant(command.getApplicant());
        sealUse.setApplicantId(command.getApplicantId());
        sealUse.setDeptId(command.getDeptId());
        sealUse.setSealId(command.getSealId());
        sealUse.setSealName(command.getSealName());
        sealUse.setReason(command.getReason());
        sealUse.setUseTime(command.getUseTime());
        sealUse.setRemark(command.getRemark());
    }

    /**
     * 将用章文件命令转换为用章文件领域对象。
     *
     * @param commands 用章文件命令列表
     * @return 用章文件列表
     */
    private List<SealUseFile> buildFiles(List<AdministrativeCommands.SealUseFileCommand> commands) {
        if (commands == null) {
            return List.of();
        }
        return commands.stream().map(command -> {
            SealUseFile file = new SealUseFile();
            file.setFileId(command.getFileId());
            file.setFileName(command.getFileName());
            return file;
        }).toList();
    }

    /**
     * 为用章申请注册审批实例。
     *
     * @param sealUse 用章申请
     */
    private void registerApproval(SealUse sealUse) {
        if (sealUse.getApprovalInstanceId() != null) {
            return;
        }
        ApprovalInstanceRegisterCommand command = new ApprovalInstanceRegisterCommand();
        command.setBusinessType(BIZ_TYPE_SEAL);
        command.setBusinessId(sealUse.getId());
        command.setBusinessCode(sealUse.getCode());
        command.setTitle(StringUtils.hasText(sealUse.getSealName())
                ? "用章申请-" + sealUse.getSealName() : "用章申请-" + sealUse.getCode());
        command.setInitiatorId(sealUse.getApplicantId());
        command.setInitiatorName(sealUse.getApplicant());
        command.setRemark(sealUse.getRemark());
        ApprovalInstanceView instance = approvalInstanceAppService.register(command);
        sealUse.setApprovalInstanceId(instance.getId());
        repository.updateSealUse(sealUse);
    }

    /**
     * 查询用章申请，不存在时抛出业务异常。
     *
     * @param id 用章申请 ID
     * @return 用章申请
     */
    private SealUse requireSealUse(Long id) {
        return repository.findSealUseById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.SEAL_NOT_FOUND));
    }

    /**
     * 将用章申请领域对象转换为视图。
     *
     * @param sealUse 用章申请
     * @return 用章申请视图
     */
    private AdministrativeViews.SealUseView toView(SealUse sealUse) {
        AdministrativeViews.SealUseView view = new AdministrativeViews.SealUseView();
        view.setId(sealUse.getId());
        view.setCode(sealUse.getCode());
        view.setApplicant(sealUse.getApplicant());
        view.setApplicantId(sealUse.getApplicantId());
        view.setDeptId(sealUse.getDeptId());
        view.setSealId(sealUse.getSealId());
        view.setSealName(sealUse.getSealName());
        view.setReason(sealUse.getReason());
        view.setFileCount(sealUse.getFileCount());
        view.setUseTime(sealUse.getUseTime());
        view.setStatus(sealUse.getStatus());
        view.setRegisteredTime(sealUse.getRegisteredTime());
        view.setRegisteredBy(sealUse.getRegisteredBy());
        view.setApprovalInstanceId(sealUse.getApprovalInstanceId());
        view.setRemark(sealUse.getRemark());
        view.setFiles(sealUse.getFiles().stream().map(file -> {
            AdministrativeViews.FileView fileView = new AdministrativeViews.FileView();
            fileView.setId(file.getId());
            fileView.setFileId(file.getFileId());
            fileView.setFileName(file.getFileName());
            return fileView;
        }).toList());
        view.setCreateTime(sealUse.getCreateTime());
        return view;
    }
}
