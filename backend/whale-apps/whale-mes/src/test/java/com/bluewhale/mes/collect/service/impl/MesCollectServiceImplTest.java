package com.bluewhale.mes.collect.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mes.collect.dto.MesCollectPointSaveRequest;
import com.bluewhale.mes.collect.dto.MesMetricIngestRequest;
import com.bluewhale.mes.collect.entity.MesArchivePolicy;
import com.bluewhale.mes.collect.entity.MesCollectBoardConfig;
import com.bluewhale.mes.collect.entity.MesCollectDevice;
import com.bluewhale.mes.collect.entity.MesCollectionPoint;
import com.bluewhale.mes.collect.entity.MesMetricSample;
import com.bluewhale.mes.collect.mapper.MesArchivePolicyMapper;
import com.bluewhale.mes.collect.mapper.MesCollectBoardConfigMapper;
import com.bluewhale.mes.collect.mapper.MesCollectDeviceMapper;
import com.bluewhale.mes.collect.mapper.MesCollectionPointMapper;
import com.bluewhale.mes.collect.mapper.MesMetricSampleMapper;
import com.bluewhale.mes.collect.vo.MesArchivePolicyVO;
import com.bluewhale.mes.collect.vo.MesMetricIngestResultVO;
import com.bluewhale.mes.equipment.entity.MesEquipment;
import com.bluewhale.mes.equipment.mapper.MesEquipmentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 数据采集服务单元测试（M4）。
 *
 * <p>覆盖采集点编码唯一、样本幂等、归档执行可重跑。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesCollectServiceImplTest {

    @Mock
    private MesCollectDeviceMapper deviceMapper;

    @Mock
    private MesCollectionPointMapper pointMapper;

    @Mock
    private MesMetricSampleMapper sampleMapper;

    @Mock
    private MesCollectBoardConfigMapper boardMapper;

    @Mock
    private MesArchivePolicyMapper archivePolicyMapper;

    @Mock
    private MesEquipmentMapper equipmentMapper;

    @InjectMocks
    private MesCollectServiceImpl collectService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(MesCollectDevice.class, MesCollectionPoint.class, MesMetricSample.class,
                MesCollectBoardConfig.class, MesArchivePolicy.class, MesEquipment.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @Test
    @DisplayName("采集点编码租户内唯一")
    void shouldRejectDuplicatePointCode() {
        MesCollectPointSaveRequest request = new MesCollectPointSaveRequest();
        request.setPointCode("PT-001");
        request.setPointName("温度采集");
        when(pointMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class, () -> collectService.savePoint(request));
        assertTrue(exception.getMessage().contains("采集点编码已存在"));
        verify(pointMapper, never()).insert(any(MesCollectionPoint.class));
    }

    @Test
    @DisplayName("样本按采集点/时间/序号幂等")
    void shouldSkipDuplicateSamples() {
        MesCollectionPoint point = new MesCollectionPoint();
        point.setId(2L);
        point.setPointCode("PT-001");
        point.setEnabled(1);
        point.setUnit("℃");
        when(pointMapper.selectOne(any())).thenReturn(point);

        MesMetricIngestRequest.SampleItem first = new MesMetricIngestRequest.SampleItem();
        first.setMetricValue(new BigDecimal("36.5"));
        first.setSampledAt(LocalDateTime.of(2026, 11, 5, 8, 0));
        MesMetricIngestRequest.SampleItem second = new MesMetricIngestRequest.SampleItem();
        second.setMetricValue(new BigDecimal("36.8"));
        second.setSampledAt(LocalDateTime.of(2026, 11, 5, 8, 1));
        MesMetricIngestRequest request = new MesMetricIngestRequest();
        request.setSamples(List.of(first, second));
        when(sampleMapper.selectCount(any())).thenReturn(1L, 0L);

        MesMetricIngestResultVO result = collectService.ingest("PT-001", request);
        assertEquals(1, result.getAccepted());
        assertEquals(1, result.getSkipped());
        verify(sampleMapper, times(1)).insert(any(MesMetricSample.class));
    }

    @Test
    @DisplayName("归档执行可重跑且候选计数稳定")
    void shouldRunArchiveIdempotently() {
        MesArchivePolicy policy = new MesArchivePolicy();
        policy.setId(7L);
        policy.setPolicyCode("AP-001");
        policy.setTableName("mes_cl_metric_sample");
        policy.setRetentionDays(30);
        policy.setStatus("ENABLED");
        when(archivePolicyMapper.selectById(7L)).thenReturn(policy);
        when(archivePolicyMapper.updateById(any(MesArchivePolicy.class))).thenReturn(1);
        when(sampleMapper.selectCount(any())).thenReturn(5L);

        MesArchivePolicyVO first = collectService.runArchive(7L);
        MesArchivePolicyVO second = collectService.runArchive(7L);
        assertEquals(5L, first.getLastCandidateCount());
        assertEquals(5L, second.getLastCandidateCount());
    }
}
