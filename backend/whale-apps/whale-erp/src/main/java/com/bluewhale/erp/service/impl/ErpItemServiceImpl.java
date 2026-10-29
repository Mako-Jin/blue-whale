package com.bluewhale.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.converter.ErpConverter;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.dto.ErpItemQueryRequest;
import com.bluewhale.erp.dto.ErpItemSaveRequest;
import com.bluewhale.erp.entity.ErpItem;
import com.bluewhale.erp.mapper.ErpItemMapper;
import com.bluewhale.erp.service.ErpItemService;
import com.bluewhale.erp.vo.ErpItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 物料服务实现
 *
 * <p>物料编码在租户内唯一（逻辑删除行仍占用，避免复用冲突）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpItemServiceImpl implements ErpItemService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final ErpItemMapper erpItemMapper;

    @Override
    public PageResult<ErpItemVO> page(ErpItemQueryRequest request) {
        LambdaQueryWrapper<ErpItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getCode()), ErpItem::getCode, request.getCode());
        wrapper.like(StringUtils.hasText(request.getName()), ErpItem::getName, request.getName());
        wrapper.eq(StringUtils.hasText(request.getStatus()), ErpItem::getStatus, request.getStatus());
        wrapper.orderByDesc(ErpItem::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<ErpItem> page = erpItemMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<ErpItemVO> records = page.getRecords().stream().map(ErpItemServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ErpItemVO getDetail(Long id) {
        return toVo(requireItem(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErpItemVO create(ErpItemSaveRequest request) {
        assertCodeUnique(request.getCode(), null);
        ErpItem item = ErpConverter.INSTANCE.toItemEntity(request);
        erpItemMapper.insert(item);
        log.info("物料已创建: id={}, code={}", item.getId(), item.getCode());
        return toVo(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErpItemVO update(Long id, ErpItemSaveRequest request) {
        ErpItem item = requireItem(id);
        assertCodeUnique(request.getCode(), id);
        ErpConverter.INSTANCE.updateItemEntity(request, item);
        erpItemMapper.updateById(item);
        log.info("物料已更新: id={}, code={}", id, item.getCode());
        return toVo(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireItem(id);
        erpItemMapper.deleteById(id);
        log.info("物料已删除: id={}", id);
    }

    /**
     * 按 ID 查询未删除的物料。
     *
     * @param id 主键ID
     * @return 物料实体
     */
    private ErpItem requireItem(Long id) {
        ErpItem item = id == null ? null : erpItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "物料不存在");
        }
        return item;
    }

    /**
     * 校验物料编码在租户内唯一。
     *
     * @param code      物料编码
     * @param excludeId 更新时排除自身ID，新增传 {@code null}
     */
    private void assertCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<ErpItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ErpItem::getCode, code);
        if (excludeId != null) {
            wrapper.ne(ErpItem::getId, excludeId);
        }
        if (erpItemMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "物料编码已存在");
        }
    }

    /**
     * 实体转视图对象
     *
     * @param item 物料实体
     * @return 视图对象
     */
    private static ErpItemVO toVo(ErpItem item) {
        return ErpConverter.INSTANCE.toItemVo(item);
    }
}
