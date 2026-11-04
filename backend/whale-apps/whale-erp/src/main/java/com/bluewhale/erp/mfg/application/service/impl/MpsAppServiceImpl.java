package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.mfg.application.command.MpsCreateCommand;
import com.bluewhale.erp.mfg.application.service.MpsAppService;
import com.bluewhale.erp.mfg.application.view.MpsPlanView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.MfgStatus;
import com.bluewhale.erp.mfg.domain.model.MpsPlan;
import com.bluewhale.erp.mfg.domain.model.MpsPlanLine;
import com.bluewhale.erp.mfg.domain.repository.MpsRepository;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 主生产计划应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MpsAppServiceImpl implements MpsAppService {

    /** 期间格式 */
    private static final String PERIOD_PATTERN = "\\d{4}-\\d{2}";

    private final MpsRepository mpsRepository;

    private final ItemCatalog itemCatalog;

    @Override
    public PageResult<MpsPlanView> page(MfgQuery query) {
        List<MpsPlanView> records = mpsRepository.page(query).stream().map(MpsPlanView::of).toList();
        return PageResult.of(records, mpsRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public MpsPlanView detail(Long id) {
        return MpsPlanView.of(requirePlan(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MpsPlanView create(MpsCreateCommand command) {
        String period = requirePeriod(command.getPeriod());
        List<MpsPlanLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (MpsCreateCommand.Line source : command.getLines()) {
            ItemSnapshot item = requireItem(source.getItemCode());
            MpsPlanLine line = new MpsPlanLine();
            line.setLineNo(lineNo++);
            line.setItemId(item.getId());
            line.setItemCode(item.getCode());
            line.setItemName(item.getName());
            line.setUom(source.getUom());
            line.setPlannedQuantity(source.getPlannedQuantity());
            line.setReleasedQuantity(BigDecimal.ZERO);
            lines.add(line);
        }
        MpsPlan plan = new MpsPlan();
        plan.setPlanNo(mpsRepository.nextPlanNo(period));
        plan.setPeriod(period);
        plan.setStatus(MfgStatus.DRAFT);
        plan.setRemark(command.getRemark());
        plan.setLines(lines);
        plan.calculateTotal();
        mpsRepository.insert(plan);
        return MpsPlanView.of(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MpsPlanView release(Long id) {
        MpsPlan plan = requirePlan(id);
        plan.release();
        mpsRepository.update(plan);
        return MpsPlanView.of(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MpsPlanView cancel(Long id) {
        MpsPlan plan = requirePlan(id);
        plan.cancel();
        mpsRepository.update(plan);
        return MpsPlanView.of(plan);
    }

    /**
     * 查询主生产计划，不存在按 404 处理。
     *
     * @param id 主键
     * @return 主生产计划
     */
    private MpsPlan requirePlan(Long id) {
        return mpsRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.MPS_NOT_FOUND));
    }

    /**
     * 校验物料存在且启用。
     *
     * @param itemCode 物料编码
     * @return 物料快照
     */
    private ItemSnapshot requireItem(String itemCode) {
        ItemSnapshot item = itemCatalog.findByCode(itemCode)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + itemCode + " 不存在"));
        if (!MasterDataStatus.ENABLED.equals(item.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + item.getCode() + " 已停用");
        }
        return item;
    }

    /**
     * 校验期间格式（YYYY-MM）。
     *
     * @param period 期间
     * @return 期间
     */
    private String requirePeriod(String period) {
        String value = period == null ? "" : period.trim();
        if (!value.matches(PERIOD_PATTERN)) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_INVALID, "计划期间格式应为 YYYY-MM");
        }
        return value;
    }
}
