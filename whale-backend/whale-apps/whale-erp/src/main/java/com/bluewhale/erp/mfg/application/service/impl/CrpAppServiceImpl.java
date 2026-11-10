package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.mfg.application.service.CrpAppService;
import com.bluewhale.erp.mfg.application.view.CrpView;
import com.bluewhale.erp.mfg.domain.model.CrpCalculator;
import com.bluewhale.erp.mfg.domain.model.CrpResult;
import com.bluewhale.erp.mfg.domain.model.WorkCenter;
import com.bluewhale.erp.mfg.domain.repository.ProductionOrderRepository;
import com.bluewhale.erp.mfg.domain.repository.WorkCenterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * CRP 应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CrpAppServiceImpl implements CrpAppService {

    /** 默认查询天数 */
    private static final int DEFAULT_DAYS = 30;

    private final ProductionOrderRepository productionOrderRepository;

    private final WorkCenterRepository workCenterRepository;

    @Override
    public CrpView load(LocalDate fromDate, LocalDate toDate, String workCenterCode) {
        LocalDate from = fromDate == null ? LocalDate.now() : fromDate;
        LocalDate to = toDate == null ? from.plusDays(DEFAULT_DAYS - 1) : toDate;
        List<WorkCenter> centers = workCenterRepository.listAll();
        if (workCenterCode != null && !workCenterCode.isBlank()) {
            String code = workCenterCode.trim();
            boolean exists = centers.stream().anyMatch(center -> code.equals(center.getCode()));
            if (!exists) {
                throw ErpErrorCode.exception(ErpErrorCode.WORK_CENTER_NOT_FOUND);
            }
        }
        CrpResult result = CrpCalculator.calculate(productionOrderRepository.listActiveForCrp(from, to),
                centers, from, to);
        if (workCenterCode != null && !workCenterCode.isBlank()) {
            String code = workCenterCode.trim();
            result.setLoads(result.getLoads().stream()
                    .filter(bucket -> code.equals(bucket.getWorkCenterCode())).toList());
            result.setSchedule(result.getSchedule().stream()
                    .filter(slot -> code.equals(slot.getWorkCenterCode())).toList());
        }
        return CrpView.of(result);
    }
}
