package com.bluewhale.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.converter.ErpConverter;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.dto.ErpSupplierQueryRequest;
import com.bluewhale.erp.dto.ErpSupplierSaveRequest;
import com.bluewhale.erp.entity.ErpSupplier;
import com.bluewhale.erp.mapper.ErpSupplierMapper;
import com.bluewhale.erp.service.ErpSupplierService;
import com.bluewhale.erp.vo.ErpSupplierVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 供应商主数据服务实现。
 *
 * <p>供应商编码在租户内唯一（逻辑删除行仍占用，避免复用冲突）。本服务只承接主数据，
 * 准入、资质、绩效等 SRM 流程不在此实现。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpSupplierServiceImpl implements ErpSupplierService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final ErpSupplierMapper erpSupplierMapper;

    @Override
    public PageResult<ErpSupplierVO> page(ErpSupplierQueryRequest request) {
        LambdaQueryWrapper<ErpSupplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getCode()), ErpSupplier::getCode, request.getCode());
        wrapper.like(StringUtils.hasText(request.getName()), ErpSupplier::getName, request.getName());
        wrapper.eq(StringUtils.hasText(request.getCategory()), ErpSupplier::getCategory, request.getCategory());
        wrapper.eq(StringUtils.hasText(request.getStatus()), ErpSupplier::getStatus, request.getStatus());
        wrapper.orderByDesc(ErpSupplier::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<ErpSupplier> page = erpSupplierMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<ErpSupplierVO> records = page.getRecords().stream().map(ErpSupplierServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ErpSupplierVO getDetail(Long id) {
        return toVo(requireSupplier(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErpSupplierVO create(ErpSupplierSaveRequest request) {
        assertCodeUnique(request.getCode(), null);
        ErpSupplier supplier = ErpConverter.INSTANCE.toSupplierEntity(request);
        erpSupplierMapper.insert(supplier);
        log.info("供应商已创建: id={}, code={}", supplier.getId(), supplier.getCode());
        return toVo(supplier);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErpSupplierVO update(Long id, ErpSupplierSaveRequest request) {
        ErpSupplier supplier = requireSupplier(id);
        assertCodeUnique(request.getCode(), id);
        ErpConverter.INSTANCE.updateSupplierEntity(request, supplier);
        erpSupplierMapper.updateById(supplier);
        log.info("供应商已更新: id={}, code={}", id, supplier.getCode());
        return toVo(supplier);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireSupplier(id);
        erpSupplierMapper.deleteById(id);
        log.info("供应商已删除: id={}", id);
    }

    /**
     * 按 ID 查询未删除的供应商。
     *
     * @param id 主键ID
     * @return 供应商实体
     */
    private ErpSupplier requireSupplier(Long id) {
        ErpSupplier supplier = id == null ? null : erpSupplierMapper.selectById(id);
        if (supplier == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.erp.service.supplier-found", "供应商不存在");
        }
        return supplier;
    }

    /**
     * 校验供应商编码在租户内唯一。
     *
     * @param code      供应商编码
     * @param excludeId 更新时排除自身ID，新增传 {@code null}
     */
    private void assertCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<ErpSupplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ErpSupplier::getCode, code);
        if (excludeId != null) {
            wrapper.ne(ErpSupplier::getId, excludeId);
        }
        if (erpSupplierMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.erp.service.supplier-code-already-exists", "供应商编码已存在");
        }
    }

    /**
     * 实体转视图对象。
     *
     * @param supplier 供应商实体
     * @return 视图对象
     */
    private static ErpSupplierVO toVo(ErpSupplier supplier) {
        return ErpConverter.INSTANCE.toSupplierVo(supplier);
    }
}
