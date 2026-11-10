package com.bluewhale.mall.aftersale.application.job;

import com.bluewhale.mall.aftersale.application.service.AfterSaleAppService;
import com.bluewhale.mall.aftersale.domain.model.MallAfterSale;
import com.bluewhale.mall.config.MallProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AfterSaleExpireJob} 单元测试。
 *
 * <p>覆盖关闭开关（expire=0）、逐单独立事务（单失败不影响其余）与空扫描短路。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AfterSaleExpireJobTest {

    @Mock
    private AfterSaleAppService afterSaleAppService;

    @Mock
    private MallProperties properties;

    @InjectMocks
    private AfterSaleExpireJob job;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getAfterSaleExpireHours()).thenReturn(48);
    }

    /**
     * 构造一张超时售后单。
     *
     * @param id ID
     * @return 售后单
     */
    private MallAfterSale expiredSale(long id) {
        MallAfterSale afterSale = new MallAfterSale();
        afterSale.setId(id);
        afterSale.setAfterSaleNo("AS2026110700000" + id);
        return afterSale;
    }

    @Test
    @DisplayName("expire=0 时关闭开关生效，不发起任何扫描")
    void skipsWhenDisabled() {
        when(properties.getAfterSaleExpireHours()).thenReturn(0);

        job.closeExpired();

        verifyNoInteractions(afterSaleAppService);
    }

    @Test
    @DisplayName("扫描结果为空时直接返回，不逐单关闭")
    void skipsWhenNothingExpired() {
        when(afterSaleAppService.listExpiredCreated(eq(48), anyInt())).thenReturn(List.of());

        job.closeExpired();

        verify(afterSaleAppService).listExpiredCreated(eq(48), anyInt());
        verify(afterSaleAppService, never()).closeExpired(anyLong());
    }

    @Test
    @DisplayName("逐单独立事务：一单关闭失败不影响其余，处理数照常统计")
    void closesEachIndependently() {
        when(afterSaleAppService.listExpiredCreated(eq(48), anyInt()))
                .thenReturn(List.of(expiredSale(1L), expiredSale(2L), expiredSale(3L)));
        doThrow(new RuntimeException("额度已被释放")).when(afterSaleAppService).closeExpired(2L);

        job.closeExpired();

        verify(afterSaleAppService).closeExpired(1L);
        verify(afterSaleAppService).closeExpired(2L);
        verify(afterSaleAppService).closeExpired(3L);
    }
}
