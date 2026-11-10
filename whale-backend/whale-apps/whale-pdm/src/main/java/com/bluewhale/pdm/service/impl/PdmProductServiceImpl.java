package com.bluewhale.pdm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.pdm.converter.PdmConverter;
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
import com.bluewhale.pdm.part.support.PdmPartTypes;
import com.bluewhale.pdm.service.PdmProductService;
import com.bluewhale.pdm.vo.PdmProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 产品服务实现
 *
 * <p>产品编码在租户内唯一（逻辑删除行仍占用，避免复用冲突）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmProductServiceImpl implements PdmProductService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final PdmProductMapper pdmProductMapper;

    private final PdmPartCategoryMapper categoryMapper;

    private final PdmBomMapper bomMapper;

    private final PdmBomLineMapper bomLineMapper;

    @Override
    public PageResult<PdmProductVO> page(PdmProductQueryRequest request) {
        LambdaQueryWrapper<PdmProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getCode()), PdmProduct::getCode, request.getCode());
        wrapper.like(StringUtils.hasText(request.getName()), PdmProduct::getName, request.getName());
        wrapper.eq(StringUtils.hasText(request.getPartType()), PdmProduct::getPartType, request.getPartType());
        wrapper.eq(StringUtils.hasText(request.getCategoryCode()), PdmProduct::getCategoryCode,
                request.getCategoryCode());
        wrapper.eq(StringUtils.hasText(request.getSourceType()), PdmProduct::getSourceType,
                request.getSourceType());
        wrapper.eq(StringUtils.hasText(request.getStatus()), PdmProduct::getStatus, request.getStatus());
        wrapper.orderByDesc(PdmProduct::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PdmProduct> page = pdmProductMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<PdmProductVO> records = page.getRecords().stream().map(PdmProductServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PdmProductVO getDetail(Long id) {
        return toVo(requireProduct(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmProductVO create(PdmProductSaveRequest request) {
        assertCodeUnique(request.getCode(), null);
        normalize(request);
        assertCategoryExists(request.getCategoryCode());
        PdmProduct product = PdmConverter.INSTANCE.toEntity(request);
        pdmProductMapper.insert(product);
        log.info("产品已创建: id={}, code={}", product.getId(), product.getCode());
        return toVo(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmProductVO update(Long id, PdmProductSaveRequest request) {
        PdmProduct product = requireProduct(id);
        assertCodeUnique(request.getCode(), id);
        normalize(request);
        assertCategoryExists(request.getCategoryCode());
        PdmConverter.INSTANCE.updateEntity(request, product);
        pdmProductMapper.updateById(product);
        log.info("产品已更新: id={}, code={}", id, product.getCode());
        return toVo(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PdmProduct product = requireProduct(id);
        long bomCount = bomMapper.selectCount(new LambdaQueryWrapper<PdmBom>()
                .eq(PdmBom::getProductId, id));
        long lineCount = bomLineMapper.selectCount(new LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getChildPartId, id));
        if (bomCount > 0 || lineCount > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "零件已被 BOM 引用，不能删除");
        }
        pdmProductMapper.deleteById(id);
        log.info("产品已删除: id={}", product.getId());
    }

    /**
     * 按 ID 查询未删除的产品。
     *
     * @param id 主键ID
     * @return 产品实体
     */
    private PdmProduct requireProduct(Long id) {
        PdmProduct product = id == null ? null : pdmProductMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "产品不存在");
        }
        return product;
    }

    /**
     * 归一化零件主档默认值（避免显式插入 NULL 绕过列默认值）。
     *
     * @param request 保存请求
     */
    private void normalize(PdmProductSaveRequest request) {
        if (!StringUtils.hasText(request.getPartType())) {
            request.setPartType(PdmPartTypes.PART);
        }
        if (!StringUtils.hasText(request.getUom())) {
            request.setUom("PCS");
        }
        if (!StringUtils.hasText(request.getSourceType())) {
            request.setSourceType(PdmPartTypes.UNIQUE);
        }
    }

    /**
     * 校验物料分类编码存在（为空时跳过）。
     *
     * @param categoryCode 分类编码
     */
    private void assertCategoryExists(String categoryCode) {
        if (!StringUtils.hasText(categoryCode)) {
            return;
        }
        long count = categoryMapper.selectCount(new LambdaQueryWrapper<PdmPartCategory>()
                .eq(PdmPartCategory::getCode, categoryCode.trim()));
        if (count == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "物料分类不存在");
        }
    }

    /**
     * 校验产品编码在租户内唯一。
     *
     * @param code      产品编码
     * @param excludeId 更新时排除自身ID，新增传 {@code null}
     */
    private void assertCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<PdmProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PdmProduct::getCode, code);
        if (excludeId != null) {
            wrapper.ne(PdmProduct::getId, excludeId);
        }
        if (pdmProductMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "产品编码已存在");
        }
    }

    /**
     * 实体转视图对象
     *
     * @param product 产品实体
     * @return 视图对象
     */
    private static PdmProductVO toVo(PdmProduct product) {
        return PdmConverter.INSTANCE.toProductVo(product);
    }
}
