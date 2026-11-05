package com.bluewhale.pdm.part.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.mapper.PdmProductMapper;
import com.bluewhale.pdm.part.dto.PdmBomDeriveRequest;
import com.bluewhale.pdm.part.dto.PdmBomLineSaveRequest;
import com.bluewhale.pdm.part.dto.PdmBomSaveRequest;
import com.bluewhale.pdm.part.entity.PdmBom;
import com.bluewhale.pdm.part.entity.PdmBomDiffTask;
import com.bluewhale.pdm.part.entity.PdmBomLine;
import com.bluewhale.pdm.part.entity.PdmBomRelease;
import com.bluewhale.pdm.part.mapper.PdmBomDiffTaskMapper;
import com.bluewhale.pdm.part.mapper.PdmBomLineMapper;
import com.bluewhale.pdm.part.mapper.PdmBomMapper;
import com.bluewhale.pdm.part.mapper.PdmBomReleaseMapper;
import com.bluewhale.pdm.part.support.PdmBomDiffTaskStatus;
import com.bluewhale.pdm.part.support.PdmBomSnapshotBuilder;
import com.bluewhale.pdm.part.support.PdmBomStatus;
import com.bluewhale.pdm.part.vo.PdmBomDiffTaskVO;
import com.bluewhale.pdm.part.vo.PdmBomDiffVO;
import com.bluewhale.pdm.part.vo.PdmBomExportRowVO;
import com.bluewhale.pdm.part.vo.PdmBomReleaseVO;
import com.bluewhale.pdm.part.vo.PdmBomSnapshotVO;
import com.bluewhale.pdm.part.vo.PdmBomVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BOM 服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmBomServiceImplTest {

    @Mock
    private PdmBomMapper bomMapper;

    @Mock
    private PdmBomLineMapper bomLineMapper;

    @Mock
    private PdmBomReleaseMapper bomReleaseMapper;

    @Mock
    private PdmProductMapper productMapper;

    @Mock
    private PdmBomDiffTaskMapper bomDiffTaskMapper;

    @Mock
    private Executor pdmDiffExecutor;

    @InjectMocks
    private PdmBomServiceImpl bomService;

    /**
     * 初始化 MyBatis-Plus 表元数据。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PdmProduct.class);
        TableInfoHelper.initTableInfo(assistant, PdmBom.class);
        TableInfoHelper.initTableInfo(assistant, PdmBomLine.class);
        TableInfoHelper.initTableInfo(assistant, PdmBomRelease.class);
        TableInfoHelper.initTableInfo(assistant, PdmBomDiffTask.class);
    }

    /**
     * 新建 BOM 自动生成编号并递增主版本。
     */
    @Test
    void createShouldGenerateNextVersion() {
        when(productMapper.selectById(100L)).thenReturn(part(100L, "P-100", "成品", "PRODUCT"));
        when(bomMapper.selectList(any())).thenReturn(List.of(bom(1L, "BOM-P-100", "V1.0", 100L, "DRAFT")));
        when(bomMapper.insert(any(PdmBom.class))).thenAnswer(invocation -> {
            PdmBom entity = invocation.getArgument(0);
            entity.setId(2L);
            return 1;
        });

        PdmBomVO view = bomService.create(saveRequest(100L, null));

        assertEquals(2L, view.getId());
        assertEquals("BOM-P-100", view.getBomNo());
        assertEquals("V2.0", view.getVersionNo());
        assertEquals("DRAFT", view.getStatus());
    }

    /**
     * 加入自身祖先作为子件返回环检测错误。
     */
    @Test
    void addLineShouldDetectCycle() {
        when(bomMapper.selectById(1L)).thenReturn(bom(1L, "BOM-P-100", "V1.0", 100L, "DRAFT"));
        when(productMapper.selectById(200L)).thenReturn(part(200L, "A1", "部件", "ASSEMBLY"));
        when(productMapper.selectById(100L)).thenReturn(part(100L, "P-100", "成品", "PRODUCT"));
        when(bomLineMapper.selectCount(any())).thenReturn(1L);
        when(bomLineMapper.selectList(any())).thenReturn(List.of(line(1L, 100L, 200L, 1, "1")));

        PdmBomLineSaveRequest request = lineRequest(200L, 100L, "1");
        BizException exception = assertThrows(BizException.class, () -> bomService.addLine(1L, request));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().startsWith("BOM_CYCLE_DETECTED:"));
        verify(bomLineMapper, never()).insert(any(PdmBomLine.class));
    }

    /**
     * 合法加入子件并按同级最大行号追加。
     */
    @Test
    void addLineShouldInsertWithNextLineNo() {
        PdmBomLine existing = line(1L, 100L, 200L, 1, "1");
        when(bomMapper.selectById(1L)).thenReturn(bom(1L, "BOM-P-100", "V1.0", 100L, "DRAFT"));
        when(productMapper.selectById(200L)).thenReturn(part(200L, "A1", "部件", "ASSEMBLY"));
        when(productMapper.selectById(300L)).thenReturn(part(300L, "S1", "标准件", "PART"));
        when(bomLineMapper.selectCount(any())).thenReturn(1L, 0L, 0L);
        when(bomLineMapper.selectList(any())).thenReturn(List.of(existing));
        when(bomLineMapper.insert(any(PdmBomLine.class))).thenAnswer(invocation -> {
            PdmBomLine entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        });
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(
                part(100L, "P-100", "成品", "PRODUCT"), part(200L, "A1", "部件", "ASSEMBLY")));

        PdmBomVO view = bomService.addLine(1L, lineRequest(200L, 300L, "2"));

        ArgumentCaptor<PdmBomLine> captor = ArgumentCaptor.forClass(PdmBomLine.class);
        verify(bomLineMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getLineNo());
        assertEquals(300L, captor.getValue().getChildPartId());
        assertEquals(0, new BigDecimal("2").compareTo(captor.getValue().getQty()));
        assertFalse(view.getTree().isEmpty());
        assertEquals(1, view.getLines().size());
    }

    /**
     * 已存在同版本快照时重复发布直接返回既有记录。
     */
    @Test
    void releaseShouldBeIdempotent() {
        PdmBom bom = bom(1L, "B1", "V1.0", 100L, "DRAFT");
        PdmBomRelease existing = release("B1", "V1.0", "abc", 12);
        when(bomMapper.selectById(1L)).thenReturn(bom);
        when(bomReleaseMapper.selectOne(any())).thenReturn(existing);

        PdmBomReleaseVO view = bomService.release(1L, 7L);

        assertEquals("abc", view.getChecksum());
        assertEquals(12, view.getLineCount());
        verify(bomReleaseMapper, never()).insert(any(PdmBomRelease.class));
        verify(bomMapper, never()).updateById(any(PdmBom.class));
    }

    /**
     * 首次发布冻结快照、推进状态并作废旧版本。
     */
    @Test
    void releaseShouldFreezeAndObsoletePrevious() {
        PdmBom bom = bom(1L, "B1", "V1.0", 100L, "DRAFT");
        PdmBom previous = bom(2L, "B1", "V0.9", 100L, "RELEASED");
        when(bomMapper.selectById(1L)).thenReturn(bom);
        when(bomReleaseMapper.selectOne(any())).thenReturn(null);
        when(bomLineMapper.selectList(any())).thenReturn(List.of(line(1L, 100L, 200L, 1, "2")));
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(
                part(100L, "P-100", "成品", "PRODUCT"), part(200L, "A1", "部件", "ASSEMBLY")));
        when(bomReleaseMapper.insert(any(PdmBomRelease.class))).thenAnswer(invocation -> {
            PdmBomRelease entity = invocation.getArgument(0);
            entity.setId(50L);
            return 1;
        });
        when(bomMapper.selectList(any())).thenReturn(List.of(bom, previous));

        PdmBomReleaseVO view = bomService.release(1L, 7L);

        assertEquals(50L, view.getId());
        assertEquals(64, view.getChecksum().length());
        assertEquals(1, view.getLineCount());
        assertEquals(PdmBomStatus.RELEASED, bom.getStatus());
        assertEquals(PdmBomStatus.OBSOLETE, previous.getStatus());
        ArgumentCaptor<PdmBom> captor = ArgumentCaptor.forClass(PdmBom.class);
        verify(bomMapper, times(2)).updateById(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(item -> item.getId().equals(2L)
                        && PdmBomStatus.OBSOLETE.equals(item.getStatus())));
    }

    /**
     * 未知版本快照返回 404。
     */
    @Test
    void snapshotShouldThrowNotFoundWhenAbsent() {
        when(bomReleaseMapper.selectOne(any())).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> bomService.snapshot("B1", "V1.0"));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    /**
     * 同一快照连续读取结果一致。
     */
    @Test
    void snapshotShouldBeStableAcrossReads() {
        PdmBom bom = bom(1L, "B1", "V1.0", 100L, "RELEASED");
        PdmBomSnapshotVO snapshot = PdmBomSnapshotBuilder.build(bom,
                List.of(line(1L, 100L, 200L, 1, "2")),
                Map.of(100L, part(100L, "P-100", "成品", "PRODUCT"),
                        200L, part(200L, "A1", "部件", "ASSEMBLY")));
        String payload = PdmBomSnapshotBuilder.canonicalJson(snapshot);
        PdmBomRelease stored = release("B1", "V1.0", PdmBomSnapshotBuilder.checksum(payload), 1);
        stored.setPayload(payload);
        when(bomReleaseMapper.selectOne(any())).thenReturn(stored);

        PdmBomSnapshotVO first = bomService.snapshot("B1", "V1.0");
        PdmBomSnapshotVO second = bomService.snapshot("B1", "V1.0");

        assertNotNull(first.getChecksum());
        assertEquals(first.getChecksum(), second.getChecksum());
        assertEquals(first.getLines().get(0).getPath(), second.getLines().get(0).getPath());
        assertEquals("A1", first.getLines().get(0).getChildPartNo());
    }

    /**
     * 从 EBOM 派生 PBOM 时复制全部结构行。
     */
    @Test
    void deriveShouldCopySourceLines() {
        PdmBom source = bom(1L, "BOM-P-100", "V1.0", 100L, "RELEASED");
        when(bomMapper.selectById(1L)).thenReturn(source);
        when(bomMapper.selectList(any())).thenReturn(List.of());
        when(bomMapper.insert(any(PdmBom.class))).thenAnswer(invocation -> {
            PdmBom entity = invocation.getArgument(0);
            entity.setId(2L);
            return 1;
        });
        when(bomLineMapper.selectList(any())).thenReturn(List.of(line(1L, 100L, 200L, 1, "2")));
        when(bomLineMapper.insert(any(PdmBomLine.class))).thenAnswer(invocation -> {
            PdmBomLine entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        });
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(
                part(100L, "P-100", "成品", "PRODUCT"), part(200L, "A1", "部件", "ASSEMBLY")));

        PdmBomVO view = bomService.derive(1L, deriveRequest("PBOM"));

        assertEquals(2L, view.getId());
        assertEquals("BOM-P-100-PBOM", view.getBomNo());
        assertEquals("PBOM", view.getView());
        assertEquals("V1.0", view.getVersionNo());
        assertEquals(1, view.getLines().size());
        ArgumentCaptor<PdmBom> captor = ArgumentCaptor.forClass(PdmBom.class);
        verify(bomMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getSourceBomId());
        assertEquals("V1.0", captor.getValue().getSourceVersionNo());
    }

    /**
     * 已存在同来源同视图草稿时直接返回，不再复制。
     */
    @Test
    void deriveShouldReturnExistingDraft() {
        PdmBom source = bom(1L, "BOM-P-100", "V1.0", 100L, "RELEASED");
        PdmBom existing = bom(3L, "BOM-P-100-PBOM", "V1.0", 100L, "DRAFT");
        existing.setView("PBOM");
        when(bomMapper.selectById(1L)).thenReturn(source);
        when(bomMapper.selectById(3L)).thenReturn(existing);
        when(bomMapper.selectList(any())).thenReturn(List.of(existing));
        when(bomLineMapper.selectList(any())).thenReturn(List.of(line(1L, 100L, 200L, 1, "2")));
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(
                part(100L, "P-100", "成品", "PRODUCT"), part(200L, "A1", "部件", "ASSEMBLY")));

        PdmBomVO view = bomService.derive(1L, deriveRequest("PBOM"));

        assertEquals(3L, view.getId());
        verify(bomMapper, never()).insert(any(PdmBom.class));
        verify(bomLineMapper, never()).insert(any(PdmBomLine.class));
    }

    /**
     * 对比识别用量变更与新增行。
     */
    @Test
    void diffShouldReportChangedAndAddedLines() {
        PdmBom base = bom(1L, "BOM-P-100", "V1.0", 100L, "RELEASED");
        PdmBom target = bom(2L, "BOM-P-100-PBOM", "V1.0", 100L, "DRAFT");
        target.setView("PBOM");
        when(bomMapper.selectById(1L)).thenReturn(base);
        when(bomMapper.selectById(2L)).thenReturn(target);
        when(bomLineMapper.selectCount(any())).thenReturn(1L, 2L);
        when(bomLineMapper.selectList(any())).thenReturn(
                List.of(line(1L, 100L, 200L, 1, "2")),
                List.of(line(5L, 100L, 200L, 1, "3"), line(6L, 100L, 300L, 2, "1")));
        when(productMapper.selectBatchIds(any())).thenReturn(
                List.of(part(100L, "P-100", "成品", "PRODUCT"), part(200L, "A1", "部件", "ASSEMBLY")),
                List.of(part(100L, "P-100", "成品", "PRODUCT"), part(200L, "A1", "部件", "ASSEMBLY"),
                        part(300L, "S1", "标准件", "PART")));

        PdmBomDiffVO result = bomService.diff(1L, 2L);

        assertEquals(1, result.getAdded());
        assertEquals(0, result.getRemoved());
        assertEquals(1, result.getChanged());
        assertTrue(result.getLines().stream().anyMatch(item -> "QTY_CHANGED".equals(item.getChangeType())
                && "2".equals(item.getBaseValue()) && "3".equals(item.getTargetValue())));
        assertTrue(result.getLines().stream().anyMatch(item -> "ADDED".equals(item.getChangeType())
                && "2".equals(item.getPath()) && "S1".equals(item.getPartNo())));
    }

    /**
     * 非同一产品的 BOM 拒绝对比。
     */
    @Test
    void diffShouldRejectDifferentProducts() {
        when(bomMapper.selectById(1L)).thenReturn(bom(1L, "B1", "V1.0", 100L, "DRAFT"));
        when(bomMapper.selectById(2L)).thenReturn(bom(2L, "B2", "V1.0", 200L, "DRAFT"));

        assertThrows(BizException.class, () -> bomService.diff(1L, 2L));
        verify(bomLineMapper, never()).selectCount(any());
    }

    /**
     * 行数超过上限时拒绝在线对比。
     */
    @Test
    void diffShouldRejectOversizedBom() {
        when(bomMapper.selectById(1L)).thenReturn(bom(1L, "B1", "V1.0", 100L, "DRAFT"));
        when(bomMapper.selectById(2L)).thenReturn(bom(2L, "B2", "V1.0", 100L, "DRAFT"));
        when(bomLineMapper.selectCount(any())).thenReturn(2001L);

        BizException exception = assertThrows(BizException.class, () -> bomService.diff(1L, 2L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    /**
     * 导出行按深度优先顺序并带材质规格。
     */
    @Test
    void exportRowsShouldFollowTreeOrder() {
        when(bomMapper.selectById(1L)).thenReturn(bom(1L, "B1", "V1.0", 100L, "RELEASED"));
        when(bomLineMapper.selectList(any())).thenReturn(List.of(line(1L, 100L, 200L, 1, "2")));
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(
                part(100L, "P-100", "成品", "PRODUCT"), part(200L, "A1", "部件", "ASSEMBLY")));

        List<PdmBomExportRowVO> rows = bomService.exportRows(1L);

        assertEquals(2, rows.size());
        assertEquals("", rows.get(0).getPath());
        assertEquals("P-100", rows.get(0).getPartNo());
        assertEquals("1", rows.get(1).getPath());
        assertEquals("A1", rows.get(1).getPartNo());
        assertEquals("Q235", rows.get(1).getMaterial());
        assertEquals("M8x20", rows.get(1).getSpec());
    }

    /**
     * 异步对比任务执行成功后写入结果并可轮询。
     */
    @Test
    void createDiffTaskShouldRunAndStoreSuccess() {
        PdmBom base = bom(1L, "BOM-P-100", "V1.0", 100L, "RELEASED");
        PdmBom target = bom(2L, "BOM-P-100-PBOM", "V1.0", 100L, "DRAFT");
        target.setView("PBOM");
        when(bomMapper.selectById(1L)).thenReturn(base);
        when(bomMapper.selectById(2L)).thenReturn(target);
        when(bomLineMapper.selectList(any())).thenReturn(
                List.of(line(1L, 100L, 200L, 1, "2")),
                List.of(line(5L, 100L, 200L, 1, "3"), line(6L, 100L, 300L, 2, "1")));
        when(productMapper.selectBatchIds(any())).thenReturn(
                List.of(part(100L, "P-100", "成品", "PRODUCT"), part(200L, "A1", "部件", "ASSEMBLY")),
                List.of(part(100L, "P-100", "成品", "PRODUCT"), part(200L, "A1", "部件", "ASSEMBLY"),
                        part(300L, "S1", "标准件", "PART")));
        AtomicReference<PdmBomDiffTask> stored = new AtomicReference<>();
        when(bomDiffTaskMapper.insert(any(PdmBomDiffTask.class))).thenAnswer(invocation -> {
            PdmBomDiffTask entity = invocation.getArgument(0);
            entity.setId(9L);
            stored.set(entity);
            return 1;
        });
        when(bomDiffTaskMapper.selectById(9L)).thenAnswer(invocation -> stored.get());
        when(bomDiffTaskMapper.updateById(any(PdmBomDiffTask.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(pdmDiffExecutor).execute(any());

        PdmBomDiffTaskVO view = bomService.createDiffTask(1L, 2L, 7L);

        assertEquals(9L, view.getId());
        assertEquals(PdmBomDiffTaskStatus.SUCCESS, view.getStatus());
        assertNotNull(view.getDiff());
        assertEquals(1, view.getDiff().getAdded());
        assertEquals(1, view.getDiff().getChanged());
        assertFalse(stored.get().getResultJson().isEmpty());
        assertEquals(7L, stored.get().getCreatedBy());
    }

    /**
     * 失败任务回显原因且不带结果。
     */
    @Test
    void getDiffTaskShouldReturnFailure() {
        PdmBomDiffTask task = new PdmBomDiffTask();
        task.setId(9L);
        task.setBaseBomId(1L);
        task.setTargetBomId(2L);
        task.setStatus(PdmBomDiffTaskStatus.FAILED);
        task.setErrorMessage("对比失败: BOM 不存在");
        when(bomDiffTaskMapper.selectById(9L)).thenReturn(task);

        PdmBomDiffTaskVO view = bomService.getDiffTask(9L);

        assertEquals(PdmBomDiffTaskStatus.FAILED, view.getStatus());
        assertEquals("对比失败: BOM 不存在", view.getErrorMessage());
        assertNull(view.getDiff());
    }

    /**
     * 任务不存在时报参数错误。
     */
    @Test
    void getDiffTaskShouldRejectMissing() {
        when(bomDiffTaskMapper.selectById(9L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> bomService.getDiffTask(9L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    /**
     * 构造 BOM 头。
     *
     * @param id       主键
     * @param bomNo    编号
     * @param version  版本
     * @param product  产品ID
     * @param status   状态
     * @return BOM 头
     */
    private PdmBom bom(Long id, String bomNo, String version, Long product, String status) {
        PdmBom bom = new PdmBom();
        bom.setId(id);
        bom.setBomNo(bomNo);
        bom.setVersionNo(version);
        bom.setView("EBOM");
        bom.setProductId(product);
        bom.setProductCode("P-100");
        bom.setStatus(status);
        return bom;
    }

    /**
     * 构造 BOM 行。
     *
     * @param id       主键
     * @param parentId 父件
     * @param childId  子件
     * @param lineNo   行号
     * @param qty      用量
     * @return 行
     */
    private PdmBomLine line(Long id, Long parentId, Long childId, int lineNo, String qty) {
        PdmBomLine line = new PdmBomLine();
        line.setId(id);
        line.setBomId(1L);
        line.setParentPartId(parentId);
        line.setChildPartId(childId);
        line.setLineNo(lineNo);
        line.setQty(new BigDecimal(qty));
        line.setUom("PCS");
        line.setLossRate(BigDecimal.ZERO);
        return line;
    }

    /**
     * 构造发布记录。
     *
     * @param bomNo     编号
     * @param version   版本
     * @param checksum  校验和
     * @param lineCount 行数
     * @return 发布记录
     */
    private PdmBomRelease release(String bomNo, String version, String checksum, int lineCount) {
        PdmBomRelease release = new PdmBomRelease();
        release.setId(50L);
        release.setBomId(1L);
        release.setBomNo(bomNo);
        release.setVersionNo(version);
        release.setProductCode("P-100");
        release.setView("EBOM");
        release.setChecksum(checksum);
        release.setLineCount(lineCount);
        release.setReleasedBy(7L);
        release.setReleasedAt(LocalDateTime.of(2026, 11, 4, 10, 0));
        return release;
    }

    /**
     * 构造零件。
     *
     * @param id       主键
     * @param code     编码
     * @param name     名称
     * @param partType 类型
     * @return 零件
     */
    private PdmProduct part(Long id, String code, String name, String partType) {
        PdmProduct product = new PdmProduct();
        product.setId(id);
        product.setCode(code);
        product.setName(name);
        product.setPartType(partType);
        product.setStatus("ENABLED");
        product.setUom("PCS");
        product.setMaterial("Q235");
        product.setSpec("M8x20");
        return product;
    }

    /**
     * 构造派生请求。
     *
     * @param view 目标视图
     * @return 请求
     */
    private PdmBomDeriveRequest deriveRequest(String view) {
        PdmBomDeriveRequest request = new PdmBomDeriveRequest();
        request.setView(view);
        return request;
    }

    /**
     * 构造新建请求。
     *
     * @param productId 产品ID
     * @param bomNo     编号
     * @return 请求
     */
    private PdmBomSaveRequest saveRequest(Long productId, String bomNo) {
        PdmBomSaveRequest request = new PdmBomSaveRequest();
        request.setProductId(productId);
        request.setBomNo(bomNo);
        return request;
    }

    /**
     * 构造行请求。
     *
     * @param parentId 父件
     * @param childId  子件
     * @param qty      用量
     * @return 请求
     */
    private PdmBomLineSaveRequest lineRequest(Long parentId, Long childId, String qty) {
        PdmBomLineSaveRequest request = new PdmBomLineSaveRequest();
        request.setParentPartId(parentId);
        request.setChildPartId(childId);
        request.setQty(new BigDecimal(qty));
        return request;
    }
}
