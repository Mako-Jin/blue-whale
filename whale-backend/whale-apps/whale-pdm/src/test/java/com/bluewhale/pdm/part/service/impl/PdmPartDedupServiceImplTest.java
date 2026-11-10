package com.bluewhale.pdm.part.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.mapper.PdmProductMapper;
import com.bluewhale.pdm.part.dto.PdmPartMergeRequest;
import com.bluewhale.pdm.part.entity.PdmBom;
import com.bluewhale.pdm.part.entity.PdmBomLine;
import com.bluewhale.pdm.part.mapper.PdmBomLineMapper;
import com.bluewhale.pdm.part.mapper.PdmBomMapper;
import com.bluewhale.pdm.part.vo.PdmPartDedupGroupVO;
import com.bluewhale.pdm.part.vo.PdmPartMergeResultVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 零件去重候选与人工合并单元测试
 *
 * <p>Mapper 以 Mock 隔离，覆盖归一化分组、草稿引用改写、已发布跳过、
 * 同父件用量累加与全部拒绝分支。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmPartDedupServiceImplTest {

    @Mock
    private PdmProductMapper productMapper;

    @Mock
    private PdmBomMapper bomMapper;

    @Mock
    private PdmBomLineMapper bomLineMapper;

    @InjectMocks
    private PdmPartDedupServiceImpl dedupService;

    /**
     * 初始化 MyBatis-Plus 表元数据，供 Lambda 查询条件解析列名。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PdmProduct.class);
        TableInfoHelper.initTableInfo(assistant, PdmBom.class);
        TableInfoHelper.initTableInfo(assistant, PdmBomLine.class);
    }

    /**
     * 名称/材质/规格大小写与空白差异应归入同一候选组，唯一件不返回。
     */
    @Test
    void candidatesShouldGroupByNormalizedNameMaterialAndSpec() {
        when(productMapper.selectList(any())).thenReturn(List.of(
                part(1L, "P-001", "六角螺栓", "304", "M8*30"),
                part(2L, "P-002", " 六角螺栓 ", "304 ", "m8*30"),
                part(3L, "P-003", "六角螺栓", "304", "M8*30"),
                part(4L, "P-004", "平垫圈", "Q235", "M8")));
        List<PdmPartDedupGroupVO> groups = dedupService.listCandidates();
        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).getCount());
        assertEquals("六角螺栓", groups.get(0).getName());
        assertTrue(groups.get(0).getDedupKey().contains("m8*30"));
        assertEquals(3, groups.get(0).getParts().size());
    }

    /**
     * 草稿 BOM 的源件引用应改写为保留件，源零件停用并记录合并目标。
     */
    @Test
    void mergeShouldRewriteDraftLinesAndDisableSource() {
        when(productMapper.selectById(1L)).thenReturn(part(1L, "P-001", "六角螺栓", "304", "M8*30"));
        when(productMapper.selectById(2L)).thenReturn(part(2L, "P-002", "六角螺栓", "304", "M8*30"));
        when(bomLineMapper.selectList(any())).thenReturn(List.of(line(10L, 100L, 1L), line(11L, 101L, 1L)));
        when(bomMapper.selectBatchIds(any())).thenReturn(List.of(bom(100L, "DRAFT"), bom(101L, "DRAFT")));
        when(bomLineMapper.selectOne(any())).thenReturn(null);

        PdmPartMergeResultVO result = dedupService.merge(mergeRequest(1L, 2L));

        assertEquals(2, result.getRewrittenLines());
        assertEquals(0, result.getMergedLines());
        assertEquals(0, result.getSkippedReleasedLines());
        ArgumentCaptor<PdmProduct> productCaptor = ArgumentCaptor.forClass(PdmProduct.class);
        verify(productMapper).updateById((PdmProduct) productCaptor.capture());
        assertEquals("DISABLED", productCaptor.getValue().getStatus());
        assertEquals(2L, productCaptor.getValue().getMergedTo());
        ArgumentCaptor<PdmBomLine> lineCaptor = ArgumentCaptor.forClass(PdmBomLine.class);
        verify(bomLineMapper, org.mockito.Mockito.times(2)).updateById((PdmBomLine) lineCaptor.capture());
        assertTrue(lineCaptor.getAllValues().stream().allMatch(item -> item.getChildPartId().equals(2L)));
    }

    /**
     * 已发布/作废 BOM 的引用保持原样，只计入跳过数。
     */
    @Test
    void mergeShouldSkipNonDraftBomLines() {
        when(productMapper.selectById(1L)).thenReturn(part(1L, "P-001", "六角螺栓", "304", "M8*30"));
        when(productMapper.selectById(2L)).thenReturn(part(2L, "P-002", "六角螺栓", "304", "M8*30"));
        when(bomLineMapper.selectList(any())).thenReturn(List.of(line(10L, 100L, 1L)));
        when(bomMapper.selectBatchIds(any())).thenReturn(List.of(bom(100L, "RELEASED")));

        PdmPartMergeResultVO result = dedupService.merge(mergeRequest(1L, 2L));

        assertEquals(0, result.getRewrittenLines());
        assertEquals(1, result.getSkippedReleasedLines());
        verify(bomLineMapper, never()).updateById(any(PdmBomLine.class));
    }

    /**
     * 同一父件下已存在保留件行时，用量累加并删除源行。
     */
    @Test
    void mergeShouldAccumulateQtyAndDeleteDuplicateChildLine() {
        when(productMapper.selectById(1L)).thenReturn(part(1L, "P-001", "六角螺栓", "304", "M8*30"));
        when(productMapper.selectById(2L)).thenReturn(part(2L, "P-002", "六角螺栓", "304", "M8*30"));
        PdmBomLine sourceLine = line(10L, 100L, 1L);
        sourceLine.setQty(new BigDecimal("3"));
        PdmBomLine targetLine = line(99L, 100L, 2L);
        targetLine.setQty(new BigDecimal("2"));
        when(bomLineMapper.selectList(any())).thenReturn(List.of(sourceLine));
        when(bomMapper.selectBatchIds(any())).thenReturn(List.of(bom(100L, "DRAFT")));
        when(bomLineMapper.selectOne(any())).thenReturn(targetLine);

        PdmPartMergeResultVO result = dedupService.merge(mergeRequest(1L, 2L));

        assertEquals(0, result.getRewrittenLines());
        assertEquals(1, result.getMergedLines());
        ArgumentCaptor<PdmBomLine> lineCaptor = ArgumentCaptor.forClass(PdmBomLine.class);
        verify(bomLineMapper).updateById((PdmBomLine) lineCaptor.capture());
        assertEquals(new BigDecimal("5"), lineCaptor.getValue().getQty());
        verify(bomLineMapper).deleteById(10L);
    }

    /**
     * 源件与保留件相同应拒绝。
     */
    @Test
    void mergeShouldRejectSamePart() {
        BizException exception = assertThrows(BizException.class,
                () -> dedupService.merge(mergeRequest(1L, 1L)));
        assertTrue(exception.getMessage().contains("不能相同"));
    }

    /**
     * 源件已停用或已合并应拒绝。
     */
    @Test
    void mergeShouldRejectDisabledOrMergedSource() {
        PdmProduct source = part(1L, "P-001", "六角螺栓", "304", "M8*30");
        source.setStatus("DISABLED");
        when(productMapper.selectById(1L)).thenReturn(source);
        when(productMapper.selectById(2L)).thenReturn(part(2L, "P-002", "六角螺栓", "304", "M8*30"));
        BizException exception = assertThrows(BizException.class,
                () -> dedupService.merge(mergeRequest(1L, 2L)));
        assertTrue(exception.getMessage().contains("已停用或已合并"));
    }

    /**
     * 源件为 BOM 主体时应拒绝，避免装配根断链。
     */
    @Test
    void mergeShouldRejectBomRootSource() {
        when(productMapper.selectById(1L)).thenReturn(part(1L, "P-001", "六角螺栓", "304", "M8*30"));
        when(productMapper.selectById(2L)).thenReturn(part(2L, "P-002", "六角螺栓", "304", "M8*30"));
        when(bomMapper.selectCount(any())).thenReturn(1L);
        BizException exception = assertThrows(BizException.class,
                () -> dedupService.merge(mergeRequest(1L, 2L)));
        assertTrue(exception.getMessage().contains("BOM 主体"));
    }

    /**
     * 源件在 BOM 中作为父件使用时应拒绝，避免结构断链。
     */
    @Test
    void mergeShouldRejectSourceUsedAsParent() {
        when(productMapper.selectById(1L)).thenReturn(part(1L, "P-001", "六角螺栓", "304", "M8*30"));
        when(productMapper.selectById(2L)).thenReturn(part(2L, "P-002", "六角螺栓", "304", "M8*30"));
        when(bomLineMapper.selectCount(any())).thenReturn(1L);
        BizException exception = assertThrows(BizException.class,
                () -> dedupService.merge(mergeRequest(1L, 2L)));
        assertTrue(exception.getMessage().contains("作为父件"));
    }

    /**
     * 构造零件
     */
    private static PdmProduct part(Long id, String code, String name, String material, String spec) {
        PdmProduct part = new PdmProduct();
        part.setId(id);
        part.setCode(code);
        part.setName(name);
        part.setMaterial(material);
        part.setSpec(spec);
        part.setPartType("PART");
        part.setUom("PCS");
        part.setSourceType("STANDARD");
        part.setStatus("ENABLED");
        return part;
    }

    /**
     * 构造 BOM 行
     */
    private static PdmBomLine line(Long id, Long bomId, Long childPartId) {
        PdmBomLine line = new PdmBomLine();
        line.setId(id);
        line.setBomId(bomId);
        line.setParentPartId(1L);
        line.setChildPartId(childPartId);
        line.setLineNo(1);
        line.setQty(BigDecimal.ONE);
        return line;
    }

    /**
     * 构造 BOM 头
     */
    private static PdmBom bom(Long id, String status) {
        PdmBom bom = new PdmBom();
        bom.setId(id);
        bom.setStatus(status);
        return bom;
    }

    /**
     * 构造合并请求
     */
    private static PdmPartMergeRequest mergeRequest(Long sourceId, Long targetId) {
        PdmPartMergeRequest request = new PdmPartMergeRequest();
        request.setSourceId(sourceId);
        request.setTargetId(targetId);
        request.setRemark("重复建码");
        return request;
    }
}
