package com.bluewhale.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mes.trace.dto.MesBatchSaveRequest;
import com.bluewhale.mes.trace.dto.MesTraceLinkRequest;
import com.bluewhale.mes.trace.entity.MesMaterialBatch;
import com.bluewhale.mes.trace.entity.MesTraceLink;
import com.bluewhale.mes.trace.mapper.MesMaterialBatchMapper;
import com.bluewhale.mes.trace.mapper.MesTraceLinkMapper;
import com.bluewhale.mes.trace.vo.MesBatchVO;
import com.bluewhale.mes.trace.vo.MesTraceNodeVO;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 批次与谱系服务单元测试（M2）。
 *
 * <p>覆盖批次号唯一、封批幂等、谱系成环拒绝、消耗量不超投入量、正反向逐层追溯。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesBatchServiceImplTest {

    /** 父批次 ID */
    private static final Long PARENT_ID = 1L;

    /** 子批次 ID */
    private static final Long CHILD_ID = 2L;

    @Mock
    private MesMaterialBatchMapper batchMapper;

    @Mock
    private MesTraceLinkMapper linkMapper;

    @InjectMocks
    private MesBatchServiceImpl batchService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(MesMaterialBatch.class, MesTraceLink.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    /**
     * 建档：批次号重复拒绝
     */
    @Test
    @DisplayName("批次建档：批次号唯一")
    void createShouldRejectDuplicateBatchNo() {
        when(batchMapper.selectCount(any())).thenReturn(1L);
        MesBatchSaveRequest request = new MesBatchSaveRequest();
        request.setBatchNo("B-100");
        request.setItemCode("ITEM-1");
        request.setQuantity(new BigDecimal("10"));

        assertThrows(BizException.class, () -> batchService.create(request));
        verify(batchMapper, never()).insert(any(MesMaterialBatch.class));
    }

    /**
     * 建链：父子批次相同拒绝
     */
    @Test
    @DisplayName("谱系建链：同一批次拒绝")
    void linkShouldRejectSameBatch() {
        MesTraceLinkRequest request = new MesTraceLinkRequest();
        request.setParentBatchNo("B-100");
        request.setChildBatchNo("B-100");
        request.setQuantity(new BigDecimal("10"));

        assertThrows(BizException.class, () -> batchService.link(request));
    }

    /**
     * 建链：形成环拒绝
     */
    @Test
    @DisplayName("谱系建链：成环拒绝")
    void linkShouldRejectCycle() {
        when(batchMapper.selectOne(any())).thenReturn(parent(), child());
        MesTraceLink reverse = new MesTraceLink();
        reverse.setParentBatchId(CHILD_ID);
        reverse.setChildBatchId(PARENT_ID);
        when(linkMapper.selectList(any())).thenReturn(List.of(reverse));

        assertThrows(BizException.class, () -> batchService.link(linkRequest("50")));
        verify(batchMapper, never()).updateById(any(MesMaterialBatch.class));
        verify(linkMapper, never()).insert(any(MesTraceLink.class));
    }

    /**
     * 建链：消耗量超过投入量拒绝
     */
    @Test
    @DisplayName("谱系建链：消耗量不超投入量")
    void linkShouldRejectOverConsumption() {
        MesMaterialBatch parent = parent();
        parent.setConsumedQuantity(new BigDecimal("60"));
        when(batchMapper.selectOne(any())).thenReturn(parent, child());
        when(linkMapper.selectList(any())).thenReturn(List.of());

        assertThrows(BizException.class, () -> batchService.link(linkRequest("50")));
        verify(linkMapper, never()).insert(any(MesTraceLink.class));
    }

    /**
     * 建链成功：累计消耗量与链接同事务落库
     */
    @Test
    @DisplayName("谱系建链：累计消耗量更新")
    void linkShouldAccumulateConsumedQuantity() {
        MesMaterialBatch parent = parent();
        parent.setConsumedQuantity(new BigDecimal("10"));
        when(batchMapper.selectOne(any())).thenReturn(parent, child());
        when(linkMapper.selectList(any())).thenReturn(List.of());
        when(batchMapper.updateById(any(MesMaterialBatch.class))).thenReturn(1);
        when(linkMapper.insert(any(MesTraceLink.class))).thenReturn(1);

        batchService.link(linkRequest("20"));

        assertEquals(new BigDecimal("30"), parent.getConsumedQuantity());
        verify(linkMapper).insert(any(MesTraceLink.class));
    }

    /**
     * 正向追溯：按层级返回下游节点
     */
    @Test
    @DisplayName("正向追溯：逐层展开下游")
    void forwardShouldReturnLevels() {
        MesMaterialBatch start = parent();
        when(batchMapper.selectOne(any())).thenReturn(start);
        MesTraceLink levelOne = link(PARENT_ID, CHILD_ID, "10");
        MesTraceLink levelTwo = link(CHILD_ID, 3L, "5");
        when(linkMapper.selectList(any())).thenReturn(List.of(levelOne), List.of(levelTwo), List.of());
        MesMaterialBatch child = batch(CHILD_ID, "B-200", "SEMI");
        MesMaterialBatch grandChild = batch(3L, "B-300", "FINISHED");
        when(batchMapper.selectById(CHILD_ID)).thenReturn(child);
        when(batchMapper.selectById(3L)).thenReturn(grandChild);

        List<MesTraceNodeVO> nodes = batchService.forward("B-100", 3);

        assertEquals(2, nodes.size());
        assertEquals(1, nodes.get(0).getLevel());
        assertEquals("B-200", nodes.get(0).getBatchNo());
        assertEquals(2, nodes.get(1).getLevel());
        assertEquals("B-300", nodes.get(1).getBatchNo());
    }

    /**
     * 封批：已封批幂等
     */
    @Test
    @DisplayName("批次封批：幂等")
    void closeShouldBeIdempotent() {
        MesMaterialBatch batch = parent();
        batch.setStatus("CLOSED");
        when(batchMapper.selectById(PARENT_ID)).thenReturn(batch);

        MesBatchVO vo = batchService.close(PARENT_ID);

        assertEquals("CLOSED", vo.getStatus());
        verify(batchMapper, never()).updateById(any(MesMaterialBatch.class));
    }

    /**
     * 构建建链请求
     *
     * @param quantity 消耗数量
     * @return 建链请求
     */
    private MesTraceLinkRequest linkRequest(String quantity) {
        MesTraceLinkRequest request = new MesTraceLinkRequest();
        request.setParentBatchNo("B-100");
        request.setChildBatchNo("B-200");
        request.setQuantity(new BigDecimal(quantity));
        return request;
    }

    /**
     * 构建父批次
     *
     * @return 父批次实体
     */
    private MesMaterialBatch parent() {
        MesMaterialBatch batch = batch(PARENT_ID, "B-100", "RAW");
        batch.setQuantity(new BigDecimal("100"));
        batch.setConsumedQuantity(BigDecimal.ZERO);
        return batch;
    }

    /**
     * 构建子批次
     *
     * @return 子批次实体
     */
    private MesMaterialBatch child() {
        return batch(CHILD_ID, "B-200", "SEMI");
    }

    /**
     * 构建批次实体
     *
     * @param id        批次 ID
     * @param batchNo   批次号
     * @param batchType 批次类型
     * @return 批次实体
     */
    private MesMaterialBatch batch(Long id, String batchNo, String batchType) {
        MesMaterialBatch batch = new MesMaterialBatch();
        batch.setId(id);
        batch.setBatchNo(batchNo);
        batch.setItemCode("ITEM-" + id);
        batch.setBatchType(batchType);
        batch.setQuantity(new BigDecimal("100"));
        batch.setConsumedQuantity(BigDecimal.ZERO);
        batch.setStatus("ACTIVE");
        return batch;
    }

    /**
     * 构建谱系链接
     *
     * @param parentId 父批次 ID
     * @param childId  子批次 ID
     * @param quantity 消耗数量
     * @return 链接实体
     */
    private MesTraceLink link(Long parentId, Long childId, String quantity) {
        MesTraceLink link = new MesTraceLink();
        link.setParentBatchId(parentId);
        link.setChildBatchId(childId);
        link.setQuantity(new BigDecimal(quantity));
        link.setStatus("ACTIVE");
        return link;
    }
}
