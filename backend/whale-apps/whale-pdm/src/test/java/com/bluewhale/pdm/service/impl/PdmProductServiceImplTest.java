package com.bluewhale.pdm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.dto.PdmProductQueryRequest;
import com.bluewhale.pdm.dto.PdmProductSaveRequest;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.mapper.PdmProductMapper;
import com.bluewhale.pdm.part.entity.PdmBom;
import com.bluewhale.pdm.part.entity.PdmBomLine;
import com.bluewhale.pdm.part.entity.PdmPartCategory;
import com.bluewhale.pdm.part.mapper.PdmBomLineMapper;
import com.bluewhale.pdm.part.mapper.PdmBomMapper;
import com.bluewhale.pdm.part.mapper.PdmPartCategoryMapper;
import com.bluewhale.pdm.vo.PdmProductVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PDM 产品服务单元测试（T-07 覆盖率补齐）。
 *
 * <p>覆盖分页参数归一化、编码唯一性校验、MapStruct 转换与产品详情 / 更新 / 删除，
 * Mapper 以 Mock 隔离，不依赖 MySQL。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmProductServiceImplTest {

    /** 固定时间，便于断言生成主键回填 */
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Mock
    private PdmProductMapper pdmProductMapper;

    @Mock
    private PdmPartCategoryMapper categoryMapper;

    @Mock
    private PdmBomMapper bomMapper;

    @Mock
    private PdmBomLineMapper bomLineMapper;

    @InjectMocks
    private PdmProductServiceImpl pdmProductService;

    /**
     * 初始化 MyBatis-Plus 表元数据，供 Lambda 查询条件解析列名。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PdmProduct.class);
        TableInfoHelper.initTableInfo(assistant, PdmPartCategory.class);
        TableInfoHelper.initTableInfo(assistant, PdmBom.class);
        TableInfoHelper.initTableInfo(assistant, PdmBomLine.class);
    }

    /**
     * 分页查询命中全部过滤条件，且每页条数上限截断为 200。
     */
    @Test
    void pageShouldApplyFiltersAndClampMaxSize() {
        PdmProductQueryRequest request = new PdmProductQueryRequest();
        request.setCode("PD");
        request.setName("减速器");
        request.setStatus("ENABLED");
        request.setSize(500L);
        Page<PdmProduct> page = new Page<>(1, 200);
        page.setTotal(2L);
        page.setRecords(List.of(product(1L)));
        when(pdmProductMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<PdmProductVO> result = pdmProductService.page(request);

        assertEquals(1, result.getRecords().size());
        assertEquals(2L, result.getTotal());
        assertEquals(200L, result.getSize());
    }

    /**
     * 分页查询每页条数下限收敛为 1。
     */
    @Test
    void pageShouldClampMinSize() {
        PdmProductQueryRequest request = new PdmProductQueryRequest();
        request.setSize(0L);
        Page<PdmProduct> page = new Page<>(1, 1);
        page.setRecords(List.of());
        when(pdmProductMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<PdmProductVO> result = pdmProductService.page(request);

        assertEquals(1L, result.getSize());
        assertEquals(0, result.getRecords().size());
    }

    /**
     * 详情按 ID 查询并通过 MapStruct 映射为视图。
     */
    @Test
    void getDetailShouldMapEntity() {
        when(pdmProductMapper.selectById(1L)).thenReturn(product(1L));

        PdmProductVO view = pdmProductService.getDetail(1L);

        assertEquals(1L, view.getId());
        assertEquals("PD-1", view.getCode());
        assertEquals("减速器", view.getName());
        assertEquals("ENABLED", view.getStatus());
    }

    /**
     * 详情记录不存在抛出 404。
     */
    @Test
    void getDetailShouldThrowWhenAbsent() {
        BizException exception = assertThrows(BizException.class, () -> pdmProductService.getDetail(2L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    /**
     * 新增时编码重复被拒绝，且不写库。
     */
    @Test
    void createShouldRejectDuplicatedCode() {
        when(pdmProductMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> pdmProductService.create(saveRequest("PD-1")));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(pdmProductMapper, never()).insert(any(PdmProduct.class));
    }

    /**
     * 新增成功写入实体并回写数据库生成的主键与时间。
     */
    @Test
    void createShouldInsertAndReturnView() {
        when(pdmProductMapper.selectCount(any())).thenReturn(0L);
        when(pdmProductMapper.insert(any(PdmProduct.class))).thenAnswer(invocation -> {
            PdmProduct entity = invocation.getArgument(0);
            entity.setId(9L);
            entity.setCreateTime(FIXED_TIME);
            return 1;
        });

        PdmProductVO view = pdmProductService.create(saveRequest("PD-9"));

        assertEquals(9L, view.getId());
        assertEquals("PD-9", view.getCode());
        assertEquals(FIXED_TIME, view.getCreateTime());

        ArgumentCaptor<PdmProduct> captor = ArgumentCaptor.forClass(PdmProduct.class);
        verify(pdmProductMapper).insert(captor.capture());
        assertEquals("减速器", captor.getValue().getName());
        assertEquals("图纸 V2", captor.getValue().getRemark());
        assertEquals("PART", captor.getValue().getPartType());
        assertEquals("PCS", captor.getValue().getUom());
        assertEquals("UNIQUE", captor.getValue().getSourceType());
    }

    /**
     * 新增时物料分类不存在被拒绝。
     */
    @Test
    void createShouldRejectMissingCategory() {
        when(pdmProductMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        PdmProductSaveRequest request = saveRequest("PD-10");
        request.setCategoryCode("NOPE");

        BizException exception = assertThrows(BizException.class,
                () -> pdmProductService.create(request));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(pdmProductMapper, never()).insert(any(PdmProduct.class));
    }

    /**
     * 删除时零件被 BOM 引用则拒绝。
     */
    @Test
    void deleteShouldRejectWhenReferencedByBom() {
        when(pdmProductMapper.selectById(3L)).thenReturn(product(3L));
        when(bomLineMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> pdmProductService.delete(3L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(pdmProductMapper, never()).deleteById(3L);
    }

    /**
     * 更新时编码与其他产品冲突被拒绝。
     */
    @Test
    void updateShouldRejectDuplicatedCode() {
        when(pdmProductMapper.selectById(1L)).thenReturn(product(1L));
        when(pdmProductMapper.selectCount(any())).thenReturn(2L);

        BizException exception = assertThrows(BizException.class,
                () -> pdmProductService.update(1L, saveRequest("PD-2")));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(pdmProductMapper, never()).updateById(any(PdmProduct.class));
    }

    /**
     * 更新成功覆盖既有实体字段后按主键写回。
     */
    @Test
    void updateShouldOverwriteEntityAndReturnView() {
        when(pdmProductMapper.selectById(1L)).thenReturn(product(1L));
        when(pdmProductMapper.selectCount(any())).thenReturn(0L);

        PdmProductVO view = pdmProductService.update(1L, saveRequest("PD-1-NEW"));

        assertEquals("PD-1-NEW", view.getCode());
        assertEquals(1L, view.getId());
        verify(pdmProductMapper).updateById(any(PdmProduct.class));
    }

    /**
     * 更新不存在的产品抛出 404。
     */
    @Test
    void updateShouldThrowWhenAbsent() {
        BizException exception = assertThrows(BizException.class,
                () -> pdmProductService.update(2L, saveRequest("PD-2")));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    /**
     * 删除前校验存在性，成功后下发逻辑删除。
     */
    @Test
    void deleteShouldThrowWhenAbsentAndDelegateWhenPresent() {
        BizException exception = assertThrows(BizException.class, () -> pdmProductService.delete(2L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());

        when(pdmProductMapper.selectById(3L)).thenReturn(product(3L));
        pdmProductService.delete(3L);

        verify(pdmProductMapper).deleteById(3L);
    }

    /**
     * 主键为空时直接判定不存在（不触达数据库）。
     */
    @Test
    void requireProductShouldRejectNullId() {
        BizException exception = assertThrows(BizException.class, () -> pdmProductService.getDetail(null));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(pdmProductMapper, never()).selectById(any());
    }

    /**
     * 构造产品实体。
     *
     * @param id 主键
     * @return 产品实体
     */
    private PdmProduct product(Long id) {
        PdmProduct entity = new PdmProduct();
        entity.setId(id);
        entity.setCode("PD-1");
        entity.setName("减速器");
        entity.setStatus("ENABLED");
        entity.setRemark("图纸 V2");
        entity.setCreateTime(FIXED_TIME);
        return entity;
    }

    /**
     * 构造产品保存请求。
     *
     * @param code 产品编码
     * @return 保存请求
     */
    private PdmProductSaveRequest saveRequest(String code) {
        PdmProductSaveRequest request = new PdmProductSaveRequest();
        request.setCode(code);
        request.setName("减速器");
        request.setStatus("ENABLED");
        request.setRemark("图纸 V2");
        return request;
    }
}
