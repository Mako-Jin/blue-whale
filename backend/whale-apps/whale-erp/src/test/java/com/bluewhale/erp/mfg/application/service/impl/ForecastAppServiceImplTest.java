package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.mfg.application.command.ForecastCreateCommand;
import com.bluewhale.erp.mfg.application.view.ForecastView;
import com.bluewhale.erp.mfg.domain.model.Forecast;
import com.bluewhale.erp.mfg.domain.model.ForecastLine;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.MfgStatus;
import com.bluewhale.erp.mfg.domain.repository.ForecastRepository;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ForecastAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class ForecastAppServiceImplTest {

    private final ForecastRepository forecastRepository = mock(ForecastRepository.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final ForecastAppServiceImpl service = new ForecastAppServiceImpl(forecastRepository, itemCatalog);

    @Test
    @DisplayName("新增预测：快照物料并汇总数量")
    void shouldCreateForecast() {
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(forecastRepository.nextBatchNo("2026-11")).thenReturn("FC-202611-0001");

        ForecastView view = service.create(command());

        assertEquals("FC-202611-0001", view.getBatchNo());
        assertEquals(MfgStatus.DRAFT, view.getStatus());
        assertEquals(Integer.valueOf(1), view.getVersion());
        assertEquals("物料A", view.getLines().get(0).getItemName());
        assertEquals(0, view.getTotalQuantity().compareTo(new BigDecimal("10")));
        verify(forecastRepository).insert(any(Forecast.class));
    }

    @Test
    @DisplayName("期间格式非法拒绝建单")
    void shouldRejectInvalidPeriod() {
        ForecastCreateCommand command = command();
        command.setPeriod("202611");

        BizException exception = assertThrows(BizException.class, () -> service.create(command));

        assertEquals(ErpErrorCode.MFG_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("发布预测")
    void shouldPublish() {
        Forecast forecast = draftForecast();
        when(forecastRepository.findById(1L)).thenReturn(Optional.of(forecast));

        ForecastView view = service.publish(1L);

        assertEquals(MfgStatus.PUBLISHED, view.getStatus());
        verify(forecastRepository).update(any(Forecast.class));
    }

    @Test
    @DisplayName("取消预测")
    void shouldCancel() {
        Forecast forecast = draftForecast();
        when(forecastRepository.findById(1L)).thenReturn(Optional.of(forecast));

        ForecastView view = service.cancel(1L);

        assertEquals(MfgStatus.CANCELLED, view.getStatus());
    }

    @Test
    @DisplayName("分页查询返回总条数")
    void shouldPage() {
        when(forecastRepository.page(any())).thenReturn(List.of(draftForecast()));
        when(forecastRepository.count(any())).thenReturn(1L);

        var page = service.page(new MfgQuery());

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
    }

    /**
     * 构造创建命令。
     *
     * @return 命令
     */
    private ForecastCreateCommand command() {
        ForecastCreateCommand command = new ForecastCreateCommand();
        command.setPeriod("2026-11");
        command.setVersion(1);
        ForecastCreateCommand.Line line = new ForecastCreateCommand.Line();
        line.setItemCode("ERP-001");
        line.setQuantity(new BigDecimal("10"));
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 构造草稿预测。
     *
     * @return 预测
     */
    private Forecast draftForecast() {
        Forecast forecast = new Forecast();
        forecast.setId(1L);
        forecast.setBatchNo("FC-001");
        forecast.setPeriod("2026-11");
        forecast.setVersion(1);
        forecast.setStatus(MfgStatus.DRAFT);
        ForecastLine line = new ForecastLine();
        line.setLineNo(1);
        line.setItemId(3L);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setQuantity(new BigDecimal("10"));
        forecast.setLines(List.of(line));
        forecast.setTotalQuantity(new BigDecimal("10"));
        return forecast;
    }

    /**
     * 构造物料快照。
     *
     * @return 快照
     */
    private ItemSnapshot item() {
        ItemSnapshot snapshot = new ItemSnapshot();
        snapshot.setId(3L);
        snapshot.setCode("ERP-001");
        snapshot.setName("物料A");
        snapshot.setStatus(MasterDataStatus.ENABLED);
        return snapshot;
    }
}
