package com.bluewhale.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mes.trace.dto.MesLineSideOperationRequest;
import com.bluewhale.mes.trace.dto.MesReplenishmentRequest;
import com.bluewhale.mes.trace.entity.MesLineSideMovement;
import com.bluewhale.mes.trace.entity.MesLineSideStock;
import com.bluewhale.mes.trace.entity.MesReplenishment;
import com.bluewhale.mes.trace.mapper.MesLineSideMovementMapper;
import com.bluewhale.mes.trace.mapper.MesLineSideStockMapper;
import com.bluewhale.mes.trace.mapper.MesReplenishmentMapper;
import com.bluewhale.mes.trace.vo.MesReplenishmentVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 线边仓服务单元测试（M2）。
 *
 * <p>覆盖入库建账与累加、领用扣减余额快照、库存不足拒绝、叫料请求按请求号幂等。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesLineSideServiceImplTest {

    /** 线边仓编码 */
    private static final String WAREHOUSE = "LS-01";

    /** 物料编码 */
    private static final String ITEM = "ITEM-1";

    @Mock
    private MesLineSideStockMapper stockMapper;

    @Mock
    private MesLineSideMovementMapper movementMapper;

    @Mock
    private MesReplenishmentMapper replenishmentMapper;

    @InjectMocks
    private MesLineSideServiceImpl lineSideService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(MesLineSideStock.class, MesLineSideMovement.class,
                MesReplenishment.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    /**
     * 入库：无库存行时新建并写入流水
     */
    @Test
    @DisplayName("线边入库：新建库存与流水")
    void inboundShouldCreateStock() {
        when(stockMapper.selectOne(any())).thenReturn(null);
        when(stockMapper.insert(any(MesLineSideStock.class))).thenAnswer(invocation -> {
            MesLineSideStock stock = invocation.getArgument(0);
            stock.setId(100L);
            return 1;
        });
        when(movementMapper.insert(any(MesLineSideMovement.class))).thenReturn(1);

        String movementNo = lineSideService.inbound(operation("8"));

        assertNotNull(movementNo);
        ArgumentCaptor<MesLineSideMovement> captor = ArgumentCaptor.forClass(MesLineSideMovement.class);
        verify(movementMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getDirection());
        assertEquals(0, captor.getValue().getBeforeQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(0, captor.getValue().getAfterQuantity().compareTo(new BigDecimal("8")));
    }

    /**
     * 入库：已有库存行按金额累加
     */
    @Test
    @DisplayName("线边入库：既有库存累加")
    void inboundShouldIncreaseExistingStock() {
        MesLineSideStock stock = stock("5");
        when(stockMapper.selectOne(any())).thenReturn(stock);
        when(stockMapper.updateById(any(MesLineSideStock.class))).thenReturn(1);
        when(movementMapper.insert(any(MesLineSideMovement.class))).thenReturn(1);

        lineSideService.inbound(operation("3"));

        assertEquals(0, stock.getQuantity().compareTo(new BigDecimal("8")));
        ArgumentCaptor<MesLineSideMovement> captor = ArgumentCaptor.forClass(MesLineSideMovement.class);
        verify(movementMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getBeforeQuantity().compareTo(new BigDecimal("5")));
        assertEquals(0, captor.getValue().getAfterQuantity().compareTo(new BigDecimal("8")));
    }

    /**
     * 领用：超过余额拒绝且不落流水
     */
    @Test
    @DisplayName("线边领用：库存不得为负")
    void issueShouldRejectNegativeStock() {
        when(stockMapper.selectOne(any())).thenReturn(stock("5"));

        assertThrows(BizException.class, () -> lineSideService.issue(operation("10")));
        verify(stockMapper, never()).updateById(any(MesLineSideStock.class));
        verify(movementMapper, never()).insert(any(MesLineSideMovement.class));
    }

    /**
     * 领用：余额扣减并记录负向流水
     */
    @Test
    @DisplayName("线边领用：扣减余额并留痕")
    void issueShouldDeductStock() {
        MesLineSideStock stock = stock("10");
        when(stockMapper.selectOne(any())).thenReturn(stock);
        when(stockMapper.updateById(any(MesLineSideStock.class))).thenReturn(1);
        when(movementMapper.insert(any(MesLineSideMovement.class))).thenReturn(1);

        lineSideService.issue(operation("4"));

        assertEquals(0, stock.getQuantity().compareTo(new BigDecimal("6")));
        ArgumentCaptor<MesLineSideMovement> captor = ArgumentCaptor.forClass(MesLineSideMovement.class);
        verify(movementMapper).insert(captor.capture());
        assertEquals(-1, captor.getValue().getDirection());
        assertEquals("ISSUE", captor.getValue().getMovementType());
        assertEquals(0, captor.getValue().getAfterQuantity().compareTo(new BigDecimal("6")));
    }

    /**
     * 叫料：相同请求号返回已有请求，不重复落库
     */
    @Test
    @DisplayName("叫料补货：请求号幂等")
    void replenishShouldBeIdempotent() {
        MesReplenishment existed = new MesReplenishment();
        existed.setId(50L);
        existed.setRequestNo("RPL-1");
        existed.setStatus("REQUESTED");
        existed.setQuantity(new BigDecimal("20"));
        when(replenishmentMapper.selectOne(any())).thenReturn(existed);

        MesReplenishmentVO vo = lineSideService.replenish(replenishment("RPL-1"));

        assertEquals("RPL-1", vo.getRequestNo());
        assertEquals("REQUESTED", vo.getStatus());
        verify(replenishmentMapper, never()).insert(any(MesReplenishment.class));
    }

    /**
     * 叫料：新请求落库为待处理
     */
    @Test
    @DisplayName("叫料补货：新请求登记")
    void replenishShouldCreateRequest() {
        when(replenishmentMapper.insert(any(MesReplenishment.class))).thenReturn(1);

        MesReplenishmentVO vo = lineSideService.replenish(replenishment(null));

        assertEquals("REQUESTED", vo.getStatus());
        assertNotNull(vo.getRequestNo());
        verify(replenishmentMapper).insert(any(MesReplenishment.class));
    }

    /**
     * 构建出入库请求
     *
     * @param quantity 数量
     * @return 出入库请求
     */
    private MesLineSideOperationRequest operation(String quantity) {
        MesLineSideOperationRequest request = new MesLineSideOperationRequest();
        request.setLineId(1L);
        request.setLineCode("LINE-1");
        request.setWarehouseCode(WAREHOUSE);
        request.setItemCode(ITEM);
        request.setBatchNo("B-100");
        request.setQuantity(new BigDecimal(quantity));
        request.setOperator("tester");
        return request;
    }

    /**
     * 构建补货请求
     *
     * @param requestNo 请求号
     * @return 补货请求
     */
    private MesReplenishmentRequest replenishment(String requestNo) {
        MesReplenishmentRequest request = new MesReplenishmentRequest();
        request.setRequestNo(requestNo);
        request.setLineId(1L);
        request.setLineCode("LINE-1");
        request.setWarehouseCode(WAREHOUSE);
        request.setItemCode(ITEM);
        request.setQuantity(new BigDecimal("20"));
        request.setRequestedBy("tester");
        return request;
    }

    /**
     * 构建库存行
     *
     * @param quantity 库存数量
     * @return 库存实体
     */
    private MesLineSideStock stock(String quantity) {
        MesLineSideStock stock = new MesLineSideStock();
        stock.setId(100L);
        stock.setLineId(1L);
        stock.setLineCode("LINE-1");
        stock.setWarehouseCode(WAREHOUSE);
        stock.setItemCode(ITEM);
        stock.setBatchNo("B-100");
        stock.setQuantity(new BigDecimal(quantity));
        stock.setSafetyQuantity(BigDecimal.ZERO);
        return stock;
    }
}
