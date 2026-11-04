package com.bluewhale.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mes.quality.dto.MesSpcSampleRequest;
import com.bluewhale.mes.quality.entity.MesInspectionPlanItem;
import com.bluewhale.mes.quality.entity.MesSpcRule;
import com.bluewhale.mes.quality.entity.MesSpcSample;
import com.bluewhale.mes.quality.mapper.MesInspectionPlanItemMapper;
import com.bluewhale.mes.quality.mapper.MesSpcRuleMapper;
import com.bluewhale.mes.quality.mapper.MesSpcSampleMapper;
import com.bluewhale.mes.quality.vo.MesSpcChartPointVO;
import com.bluewhale.mes.quality.vo.MesSpcSampleVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES SPC 服务单元测试（M3）。
 *
 * <p>覆盖样本与规则/检验项一致性、子组满额与序号校验、控制限命中留痕、图表子组聚合。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesSpcServiceImplTest {

    @Mock
    private MesSpcRuleMapper ruleMapper;

    @Mock
    private MesSpcSampleMapper sampleMapper;

    @Mock
    private MesInspectionPlanItemMapper planItemMapper;

    @InjectMocks
    private MesSpcServiceImpl spcService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(MesSpcRule.class, MesSpcSample.class, MesInspectionPlanItem.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    /**
     * 样本录入：规则与检验项不匹配拒绝
     */
    @Test
    @DisplayName("SPC 样本：必须关联规则绑定的检验项")
    void submitSampleShouldRejectMismatchedPlanItem() {
        when(ruleMapper.selectById(1L)).thenReturn(rule(2));
        MesSpcSampleRequest request = sampleRequest(99L, 2, "10");

        assertThrows(BizException.class, () -> spcService.submitSample(request));
        verify(sampleMapper, never()).insert(any(MesSpcSample.class));
    }

    /**
     * 样本录入：子组样本已满拒绝
     */
    @Test
    @DisplayName("SPC 样本：子组满额拒绝追加")
    void submitSampleShouldRejectFullSubgroup() {
        MesSpcRule rule = rule(2);
        when(ruleMapper.selectById(1L)).thenReturn(rule);
        when(planItemMapper.selectById(5L)).thenReturn(new MesInspectionPlanItem());
        when(sampleMapper.selectList(any())).thenReturn(List.of(sample(1, "8"), sample(2, "9")));

        assertThrows(BizException.class, () -> spcService.submitSample(sampleRequest(5L, 1, "10")));
        verify(sampleMapper, never()).insert(any(MesSpcSample.class));
    }

    /**
     * 样本录入：子组内样本序号重复拒绝
     */
    @Test
    @DisplayName("SPC 样本：子组内序号唯一")
    void submitSampleShouldRejectDuplicateSeq() {
        when(ruleMapper.selectById(1L)).thenReturn(rule(5));
        when(planItemMapper.selectById(5L)).thenReturn(new MesInspectionPlanItem());
        when(sampleMapper.selectList(any())).thenReturn(List.of(sample(1, "8")));
        MesSpcSampleRequest request = sampleRequest(5L, 1, "9");
        request.setSampleSeq(1);

        assertThrows(BizException.class, () -> spcService.submitSample(request));
        verify(sampleMapper, never()).insert(any(MesSpcSample.class));
    }

    /**
     * 样本录入：子组满额计算均值与极差并标记控制限命中
     */
    @Test
    @DisplayName("SPC 样本：满额计算并留痕告警")
    void submitSampleShouldEvaluateSubgroup() {
        MesSpcRule rule = rule(2);
        rule.setUcl(new BigDecimal("9"));
        rule.setLcl(new BigDecimal("1"));
        when(ruleMapper.selectById(1L)).thenReturn(rule);
        when(planItemMapper.selectById(5L)).thenReturn(new MesInspectionPlanItem());
        when(sampleMapper.selectList(any())).thenReturn(List.of(sample(1, "8")));
        when(sampleMapper.insert(any(MesSpcSample.class))).thenAnswer(invocation -> {
            MesSpcSample sample = invocation.getArgument(0);
            sample.setId(700L);
            return 1;
        });

        MesSpcSampleVO vo = spcService.submitSample(sampleRequest(5L, 1, "12"));

        assertEquals(0, vo.getSubgroupXbar().compareTo(new BigDecimal("10")));
        assertEquals(0, vo.getSubgroupRange().compareTo(new BigDecimal("4")));
        assertEquals(1, vo.getAlertFlag());
        assertNotNull(vo.getAlertRule());
        verify(sampleMapper, atLeastOnce()).updateById(any(MesSpcSample.class));
    }

    /**
     * 样本录入：未命中控制限时不告警
     */
    @Test
    @DisplayName("SPC 样本：在控时不告警")
    void submitSampleShouldStayInControl() {
        MesSpcRule rule = rule(2);
        rule.setUcl(new BigDecimal("9"));
        rule.setLcl(new BigDecimal("1"));
        when(ruleMapper.selectById(1L)).thenReturn(rule);
        when(planItemMapper.selectById(5L)).thenReturn(new MesInspectionPlanItem());
        when(sampleMapper.selectList(any())).thenReturn(List.of(sample(1, "8")));
        when(sampleMapper.insert(any(MesSpcSample.class))).thenAnswer(invocation -> {
            MesSpcSample sample = invocation.getArgument(0);
            sample.setId(700L);
            return 1;
        });

        MesSpcSampleVO vo = spcService.submitSample(sampleRequest(5L, 1, "8"));

        assertEquals(0, vo.getSubgroupXbar().compareTo(new BigDecimal("8")));
        assertEquals(0, vo.getAlertFlag());
        assertNull(vo.getAlertRule());
    }

    /**
     * 图表：按子组聚合返回点
     */
    @Test
    @DisplayName("SPC 图表：按子组聚合")
    void chartShouldGroupSubgroups() {
        MesSpcSample first = sample(1, "8");
        first.setSubgroupNo(1);
        first.setSubgroupXbar(new BigDecimal("8"));
        first.setSubgroupRange(new BigDecimal("1"));
        MesSpcSample second = sample(1, "8");
        second.setSubgroupNo(1);
        second.setSubgroupXbar(new BigDecimal("8"));
        second.setSubgroupRange(new BigDecimal("1"));
        when(sampleMapper.selectList(any())).thenReturn(List.of(first, second));

        List<MesSpcChartPointVO> points = spcService.chart(5L, 1L, 10);

        assertEquals(1, points.size());
        assertEquals(2, points.get(0).getSampleCount());
        assertEquals(0, points.get(0).getSubgroupXbar().compareTo(new BigDecimal("8")));
    }

    /**
     * 构建 SPC 规则
     *
     * @param subgroupSize 子组大小
     * @return 规则
     */
    private MesSpcRule rule(int subgroupSize) {
        MesSpcRule rule = new MesSpcRule();
        rule.setId(1L);
        rule.setRuleCode("SPR-1");
        rule.setPlanItemId(5L);
        rule.setSubgroupSize(subgroupSize);
        rule.setStatus("ENABLED");
        rule.setAlertAction("WARN");
        return rule;
    }

    /**
     * 构建样本
     *
     * @param seq   子组内序号
     * @param value 实测值
     * @return 样本
     */
    private MesSpcSample sample(int seq, String value) {
        MesSpcSample sample = new MesSpcSample();
        sample.setId((long) seq);
        sample.setRuleId(1L);
        sample.setRuleCode("SPR-1");
        sample.setPlanItemId(5L);
        sample.setSubgroupNo(1);
        sample.setSampleSeq(seq);
        sample.setSubgroupSize(2);
        sample.setMeasuredValue(new BigDecimal(value));
        sample.setAlertFlag(0);
        return sample;
    }

    /**
     * 构建样本请求
     *
     * @param planItemId 检验项 ID
     * @param subgroupNo 子组号
     * @param value      实测值
     * @return 样本请求
     */
    private MesSpcSampleRequest sampleRequest(Long planItemId, int subgroupNo, String value) {
        MesSpcSampleRequest request = new MesSpcSampleRequest();
        request.setRuleId(1L);
        request.setPlanItemId(planItemId);
        request.setSubgroupNo(subgroupNo);
        request.setMeasuredValue(new BigDecimal(value));
        return request;
    }
}
