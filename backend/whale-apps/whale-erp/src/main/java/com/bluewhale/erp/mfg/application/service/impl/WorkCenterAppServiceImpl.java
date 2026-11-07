package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.mfg.application.command.WorkCenterSaveCommand;
import com.bluewhale.erp.mfg.application.service.WorkCenterAppService;
import com.bluewhale.erp.mfg.application.view.WorkCenterView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.MfgStatus;
import com.bluewhale.erp.mfg.domain.model.WorkCenter;
import com.bluewhale.erp.mfg.domain.repository.WorkCenterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工作中心应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class WorkCenterAppServiceImpl implements WorkCenterAppService {

    /** 默认效率百分比 */
    private static final BigDecimal DEFAULT_EFFICIENCY = BigDecimal.valueOf(100);

    /** 默认工作日（周一至周五） */
    private static final String DEFAULT_WORK_DAYS = "1,2,3,4,5";

    private final WorkCenterRepository workCenterRepository;

    @Override
    public PageResult<WorkCenterView> page(MfgQuery query) {
        List<WorkCenterView> records = workCenterRepository.page(query).stream()
                .map(WorkCenterView::of).toList();
        return PageResult.of(records, workCenterRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public List<WorkCenterView> listAll() {
        return workCenterRepository.listAll().stream().map(WorkCenterView::of).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkCenterView create(WorkCenterSaveCommand command) {
        if (workCenterRepository.findByCode(command.getCode().trim()).isPresent()) {
            throw ErpErrorCode.exception(ErpErrorCode.WORK_CENTER_CODE_CONFLICT);
        }
        WorkCenter center = new WorkCenter();
        fill(center, command);
        center.setStatus(normalizeStatus(command.getStatus()));
        center.validate();
        workCenterRepository.insert(center);
        return WorkCenterView.of(center);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkCenterView update(Long id, WorkCenterSaveCommand command) {
        WorkCenter center = workCenterRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.WORK_CENTER_NOT_FOUND));
        workCenterRepository.findByCode(command.getCode().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw ErpErrorCode.exception(ErpErrorCode.WORK_CENTER_CODE_CONFLICT);
                });
        fill(center, command);
        if (command.getStatus() != null && !command.getStatus().isBlank()) {
            center.setStatus(normalizeStatus(command.getStatus()));
        }
        center.validate();
        workCenterRepository.update(center);
        return WorkCenterView.of(center);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        workCenterRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.WORK_CENTER_NOT_FOUND));
        workCenterRepository.delete(id);
    }

    /**
     * 命令写入领域模型。
     *
     * @param center  工作中心
     * @param command 保存命令
     */
    private void fill(WorkCenter center, WorkCenterSaveCommand command) {
        center.setCode(command.getCode().trim());
        center.setName(command.getName().trim());
        center.setDepartment(command.getDepartment());
        center.setDailyCapacityHours(command.getDailyCapacityHours());
        center.setEfficiencyRate(command.getEfficiencyRate() == null
                ? DEFAULT_EFFICIENCY : command.getEfficiencyRate());
        center.setWorkDays(command.getWorkDays() == null || command.getWorkDays().isBlank()
                ? DEFAULT_WORK_DAYS : command.getWorkDays());
        center.setRemark(command.getRemark());
    }

    /**
     * 归一化状态。
     *
     * @param status 状态
     * @return 合法状态
     */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return MfgStatus.ENABLED;
        }
        String normalized = status.trim().toUpperCase();
        if (!MfgStatus.ENABLED.equals(normalized) && !MfgStatus.DISABLED.equals(normalized)) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_INVALID, "工作中心状态非法：" + status);
        }
        return normalized;
    }
}
