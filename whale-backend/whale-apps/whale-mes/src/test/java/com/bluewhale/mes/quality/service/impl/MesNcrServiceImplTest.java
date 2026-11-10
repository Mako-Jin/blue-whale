package com.bluewhale.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mes.quality.dto.MesDispositionCreateRequest;
import com.bluewhale.mes.quality.dto.MesNcrCloseRequest;
import com.bluewhale.mes.quality.dto.MesNcrCreateRequest;
import com.bluewhale.mes.quality.entity.MesDisposition;
import com.bluewhale.mes.quality.entity.MesInspectionDefect;
import com.bluewhale.mes.quality.entity.MesInspectionTask;
import com.bluewhale.mes.quality.entity.MesNonconformingRecord;
import com.bluewhale.mes.quality.entity.MesReworkOrder;
import com.bluewhale.mes.quality.mapper.MesDispositionMapper;
import com.bluewhale.mes.quality.mapper.MesInspectionDefectMapper;
import com.bluewhale.mes.quality.mapper.MesNonconformingRecordMapper;
import com.bluewhale.mes.quality.mapper.MesReworkOrderMapper;
import com.bluewhale.mes.quality.vo.MesDispositionVO;
import com.bluewhale.mes.quality.vo.MesNcrVO;
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
 * MES 不合格品记录与处置服务单元测试（M3）。
 *
 * <p>覆盖 NCR 自动生成、处置数量一致性、处置执行与返工建单、关闭需结论与返工完成。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesNcrServiceImplTest {

    @Mock
    private MesNonconformingRecordMapper ncrMapper;

    @Mock
    private MesDispositionMapper dispositionMapper;

    @Mock
    private MesReworkOrderMapper reworkMapper;

    @Mock
    private MesInspectionDefectMapper defectMapper;

    @InjectMocks
    private MesNcrServiceImpl ncrService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(MesNonconformingRecord.class, MesDisposition.class,
                MesReworkOrder.class, MesInspectionDefect.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    /**
     * 自动生成 NCR：不良数量取检验不良合计
     */
    @Test
    @DisplayName("NCR 自动生成：不良数量取缺陷合计")
    void createFromInspectionTaskShouldSumDefects() {
        MesInspectionDefect defect = new MesInspectionDefect();
        defect.setDefectCode("D-1");
        defect.setQuantity(new BigDecimal("3"));
        when(defectMapper.selectList(any())).thenReturn(List.of(defect));
        when(ncrMapper.insert(any(MesNonconformingRecord.class))).thenAnswer(invocation -> {
            MesNonconformingRecord ncr = invocation.getArgument(0);
            ncr.setId(500L);
            return 1;
        });

        Long id = ncrService.createFromInspectionTask(task());

        assertEquals(500L, id);
        ArgumentCaptor<MesNonconformingRecord> captor = ArgumentCaptor.forClass(MesNonconformingRecord.class);
        verify(ncrMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getDefectQuantity().compareTo(new BigDecimal("3")));
        assertEquals("OPEN", captor.getValue().getStatus());
    }

    /**
     * 手工创建 NCR：不良数量必须大于 0
     */
    @Test
    @DisplayName("NCR 手工创建：不良数量必须大于 0")
    void createShouldRejectZeroQuantity() {
        MesNcrCreateRequest request = new MesNcrCreateRequest();
        request.setDefectQuantity(BigDecimal.ZERO);

        assertThrows(BizException.class, () -> ncrService.create(request));
        verify(ncrMapper, never()).insert(any(MesNonconformingRecord.class));
    }

    /**
     * 处置：数量与 NCR 不良数量不一致拒绝
     */
    @Test
    @DisplayName("处置：数量必须等于 NCR 不良数量")
    void createDispositionShouldRejectQuantityMismatch() {
        when(ncrMapper.selectById(1L)).thenReturn(ncr(1L, "OPEN", "5"));
        when(dispositionMapper.selectCount(any())).thenReturn(0L);
        MesDispositionCreateRequest request = dispositionRequest("6");

        assertThrows(BizException.class, () -> ncrService.createDisposition(request));
        verify(dispositionMapper, never()).insert(any(MesDisposition.class));
    }

    /**
     * 处置：创建成功后 NCR 进入 DISPOSITION
     */
    @Test
    @DisplayName("处置创建：NCR 状态推进为 DISPOSITION")
    void createDispositionShouldAdvanceNcr() {
        MesNonconformingRecord ncr = ncr(1L, "OPEN", "5");
        when(ncrMapper.selectById(1L)).thenReturn(ncr);
        when(dispositionMapper.selectCount(any())).thenReturn(0L);
        when(dispositionMapper.insert(any(MesDisposition.class))).thenAnswer(invocation -> {
            MesDisposition disposition = invocation.getArgument(0);
            disposition.setId(300L);
            return 1;
        });
        when(ncrMapper.updateById(any(MesNonconformingRecord.class))).thenReturn(1);

        MesDispositionVO vo = ncrService.createDisposition(dispositionRequest(null));

        assertEquals("DISPOSITION", ncr.getStatus());
        assertEquals(0, vo.getQuantity().compareTo(new BigDecimal("5")));
        verify(dispositionMapper).insert(any(MesDisposition.class));
    }

    /**
     * 处置执行：返工类型自动生成返工单
     */
    @Test
    @DisplayName("处置执行：返工自动生成返工单")
    void executeReworkShouldCreateReworkOrder() {
        MesDisposition disposition = disposition(300L, "CONFIRMED", "REWORK");
        when(dispositionMapper.selectById(300L)).thenReturn(disposition);
        when(ncrMapper.selectById(1L)).thenReturn(ncr(1L, "DISPOSITION", "5"));
        when(dispositionMapper.updateById(any(MesDisposition.class))).thenReturn(1);
        when(ncrMapper.updateById(any(MesNonconformingRecord.class))).thenReturn(1);
        when(reworkMapper.insert(any(MesReworkOrder.class))).thenReturn(1);

        ncrService.executeDisposition(300L);

        ArgumentCaptor<MesReworkOrder> captor = ArgumentCaptor.forClass(MesReworkOrder.class);
        verify(reworkMapper).insert(captor.capture());
        assertEquals("CREATED", captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getQuantity().compareTo(new BigDecimal("5")));
    }

    /**
     * 关闭：返工单未完成拒绝
     */
    @Test
    @DisplayName("NCR 关闭：返工未完成不可关闭")
    void closeShouldRejectUnfinishedRework() {
        when(ncrMapper.selectById(1L)).thenReturn(ncr(1L, "REWORK", "5"));
        MesReworkOrder rework = new MesReworkOrder();
        rework.setStatus("IN_PROGRESS");
        when(reworkMapper.selectList(any())).thenReturn(List.of(rework));

        assertThrows(BizException.class, () -> ncrService.close(1L, closeRequest()));
        verify(ncrMapper, never()).updateById(any(MesNonconformingRecord.class));
    }

    /**
     * 关闭：完成处置并填写结论后关闭
     */
    @Test
    @DisplayName("NCR 关闭：返工完成并填写结论")
    void closeShouldCloseWhenReworkCompleted() {
        MesNonconformingRecord ncr = ncr(1L, "REWORK", "5");
        when(ncrMapper.selectById(1L)).thenReturn(ncr);
        MesReworkOrder rework = new MesReworkOrder();
        rework.setStatus("COMPLETED");
        when(reworkMapper.selectList(any())).thenReturn(List.of(rework));
        when(ncrMapper.updateById(any(MesNonconformingRecord.class))).thenReturn(1);

        MesNcrVO vo = ncrService.close(1L, closeRequest());

        assertEquals("CLOSED", vo.getStatus());
        assertNotNull(vo.getClosedAt());
        assertEquals("返工完成，复检合格", vo.getConclusion());
    }

    /**
     * 构建检验任务
     *
     * @return 检验任务
     */
    private MesInspectionTask task() {
        MesInspectionTask task = new MesInspectionTask();
        task.setId(1L);
        task.setTaskNo("QCT-1");
        task.setWorkOrderId(1L);
        task.setWorkOrderCode("WO-1");
        task.setOperationCode("OP-10");
        task.setBatchNo("B-1");
        return task;
    }

    /**
     * 构建 NCR
     *
     * @param id        ID
     * @param status    状态
     * @param quantity  不良数量
     * @return NCR
     */
    private MesNonconformingRecord ncr(Long id, String status, String quantity) {
        MesNonconformingRecord ncr = new MesNonconformingRecord();
        ncr.setId(id);
        ncr.setNcrNo("NCR-1");
        ncr.setStatus(status);
        ncr.setDefectQuantity(new BigDecimal(quantity));
        return ncr;
    }

    /**
     * 构建处置
     *
     * @param id      ID
     * @param status  状态
     * @param type    类型
     * @return 处置
     */
    private MesDisposition disposition(Long id, String status, String type) {
        MesDisposition disposition = new MesDisposition();
        disposition.setId(id);
        disposition.setNcrId(1L);
        disposition.setNcrNo("NCR-1");
        disposition.setDispositionType(type);
        disposition.setQuantity(new BigDecimal("5"));
        disposition.setStatus(status);
        return disposition;
    }

    /**
     * 构建处置请求
     *
     * @param quantity 数量（空则取 NCR 数量）
     * @return 处置请求
     */
    private MesDispositionCreateRequest dispositionRequest(String quantity) {
        MesDispositionCreateRequest request = new MesDispositionCreateRequest();
        request.setNcrId(1L);
        request.setDispositionType("REWORK");
        request.setQuantity(quantity == null ? null : new BigDecimal(quantity));
        request.setReason("尺寸超差");
        return request;
    }

    /**
     * 构建关闭请求
     *
     * @return 关闭请求
     */
    private MesNcrCloseRequest closeRequest() {
        MesNcrCloseRequest request = new MesNcrCloseRequest();
        request.setConclusion("返工完成，复检合格");
        request.setClosedBy("tester");
        return request;
    }
}
