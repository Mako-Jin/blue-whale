package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.mfg.application.command.ForecastCreateCommand;
import com.bluewhale.erp.mfg.application.service.ForecastAppService;
import com.bluewhale.erp.mfg.application.view.ForecastView;
import com.bluewhale.erp.mfg.domain.model.Forecast;
import com.bluewhale.erp.mfg.domain.model.ForecastLine;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.MfgStatus;
import com.bluewhale.erp.mfg.domain.repository.ForecastRepository;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 需求预测应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ForecastAppServiceImpl implements ForecastAppService {

    /** 期间格式 */
    private static final String PERIOD_PATTERN = "\\d{4}-\\d{2}";

    private final ForecastRepository forecastRepository;

    private final ItemCatalog itemCatalog;

    @Override
    public PageResult<ForecastView> page(MfgQuery query) {
        List<ForecastView> records = forecastRepository.page(query).stream().map(ForecastView::of).toList();
        return PageResult.of(records, forecastRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public ForecastView detail(Long id) {
        return ForecastView.of(requireForecast(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ForecastView create(ForecastCreateCommand command) {
        String period = requirePeriod(command.getPeriod());
        Integer version = command.getVersion() == null ? 1 : command.getVersion();
        if (version < 1) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_INVALID, "预测版本号必须大于 0");
        }
        List<ForecastLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (ForecastCreateCommand.Line source : command.getLines()) {
            ItemSnapshot item = requireItem(source.getItemCode());
            ForecastLine line = new ForecastLine();
            line.setLineNo(lineNo++);
            line.setItemId(item.getId());
            line.setItemCode(item.getCode());
            line.setItemName(item.getName());
            line.setUom(source.getUom());
            line.setQuantity(source.getQuantity());
            lines.add(line);
        }
        Forecast forecast = new Forecast();
        forecast.setBatchNo(forecastRepository.nextBatchNo(period));
        forecast.setPeriod(period);
        forecast.setVersion(version);
        forecast.setStatus(MfgStatus.DRAFT);
        forecast.setRemark(command.getRemark());
        forecast.setLines(lines);
        forecast.calculateTotal();
        forecastRepository.insert(forecast);
        return ForecastView.of(forecast);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ForecastView publish(Long id) {
        Forecast forecast = requireForecast(id);
        forecast.publish();
        forecastRepository.update(forecast);
        return ForecastView.of(forecast);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ForecastView cancel(Long id) {
        Forecast forecast = requireForecast(id);
        forecast.cancel();
        forecastRepository.update(forecast);
        return ForecastView.of(forecast);
    }

    /**
     * 查询预测批次，不存在按 404 处理。
     *
     * @param id 主键
     * @return 预测批次
     */
    private Forecast requireForecast(Long id) {
        return forecastRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.FORECAST_NOT_FOUND));
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
            throw ErpErrorCode.exception(ErpErrorCode.MFG_INVALID, "预测期间格式应为 YYYY-MM");
        }
        return value;
    }
}
