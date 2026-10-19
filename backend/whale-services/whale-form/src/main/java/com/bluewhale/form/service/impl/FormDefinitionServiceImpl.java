package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.dto.FormDefinitionSaveRequest;
import com.bluewhale.form.dto.FormQueryRequest;
import com.bluewhale.form.entity.FormDefinition;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.service.FormDefinitionService;
import com.bluewhale.form.vo.FormDefinitionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 表单定义服务实现
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FormDefinitionServiceImpl implements FormDefinitionService {

    /** 表单状态：正常 */
    private static final int STATUS_NORMAL = 1;

    /** 表单版本号默认值 */
    private static final int DEFAULT_VERSION = 1;

    /** 表单结构定义最大长度（字符数）：64KB */
    private static final int MAX_SCHEMA_LENGTH = 65536;

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final FormDefinitionMapper formDefinitionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDefinitionVO create(FormDefinitionSaveRequest request) {
        assertCodeUnique(request.getCode(), null);
        validateSchemaSize(request.getSchemaJson());
        FormDefinition entity = new FormDefinition();
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setSchemaJson(request.getSchemaJson());
        entity.setVersion(request.getVersion() == null ? DEFAULT_VERSION : request.getVersion());
        entity.setStatus(request.getStatus() == null ? STATUS_NORMAL : request.getStatus());
        entity.setRemark(request.getRemark());
        formDefinitionMapper.insert(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDefinitionVO update(Long id, FormDefinitionSaveRequest request) {
        FormDefinition entity = requireForm(id);
        if (!entity.getCode().equals(request.getCode())) {
            assertCodeUnique(request.getCode(), id);
            entity.setCode(request.getCode());
        }
        validateSchemaSize(request.getSchemaJson());
        entity.setName(request.getName());
        entity.setSchemaJson(request.getSchemaJson());
        if (request.getVersion() != null) {
            entity.setVersion(request.getVersion());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        entity.setRemark(request.getRemark());
        formDefinitionMapper.updateById(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireForm(id);
        formDefinitionMapper.deleteById(id);
    }

    @Override
    public FormDefinitionVO getDetail(Long id) {
        return toVo(requireForm(id));
    }

    @Override
    public PageResult<FormDefinitionVO> page(FormQueryRequest request) {
        LambdaQueryWrapper<FormDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getName()), FormDefinition::getName, request.getName());
        wrapper.eq(StringUtils.hasText(request.getCode()), FormDefinition::getCode, request.getCode());
        wrapper.eq(request.getStatus() != null, FormDefinition::getStatus, request.getStatus());
        wrapper.orderByDesc(FormDefinition::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<FormDefinition> page = formDefinitionMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<FormDefinitionVO> records = page.getRecords().stream().map(FormDefinitionServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 查询表单定义，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 表单定义ID
     * @return 表单定义实体
     */
    private FormDefinition requireForm(Long id) {
        FormDefinition entity = formDefinitionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    /**
     * 校验表单编码唯一性：命中已存在的编码则抛参数错误
     *
     * @param code 表单编码
     * @param excludeId 需排除的当前记录ID（更新时传入，新增传 {@code null}）
     */
    private void assertCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<FormDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDefinition::getCode, code);
        wrapper.ne(excludeId != null, FormDefinition::getId, excludeId);
        Long count = formDefinitionMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "表单编码已存在");
        }
    }

    /**
     * 校验表单结构定义长度不超过 64KB
     *
     * @param schemaJson 表单结构定义JSON
     * @throws BizException 超长时抛出 {@code PARAM_ERROR}
     */
    private static void validateSchemaSize(String schemaJson) {
        if (schemaJson != null && schemaJson.length() > MAX_SCHEMA_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "表单结构定义长度不能超过 65536 字符");
        }
    }

    /**
     * 表单实体转视图对象
     *
     * @param entity 表单定义实体
     * @return 表单定义视图对象
     */
    private static FormDefinitionVO toVo(FormDefinition entity) {
        FormDefinitionVO vo = new FormDefinitionVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
