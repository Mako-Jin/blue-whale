package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.dto.FormDataQueryRequest;
import com.bluewhale.form.dto.FormDataSubmitRequest;
import com.bluewhale.form.entity.FormData;
import com.bluewhale.form.entity.FormDefinition;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.service.FormDataService;
import com.bluewhale.form.vo.FormDataVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 表单数据服务实现
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FormDataServiceImpl implements FormDataService {

    /** 数据状态：有效 */
    private static final int STATUS_NORMAL = 1;

    /** 表单数据最大长度（字符数）：64KB */
    private static final int MAX_DATA_LENGTH = 65536;

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final FormDataMapper formDataMapper;

    private final FormDefinitionMapper formDefinitionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDataVO submit(FormDataSubmitRequest request, String submitter) {
        FormDefinition definition = formDefinitionMapper.selectById(request.getDefinitionId());
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        validateDataSize(request.getDataJson());
        FormData entity = new FormData();
        entity.setDefinitionId(definition.getId());
        entity.setDefinitionCode(definition.getCode());
        entity.setDefinitionVersion(definition.getVersion());
        entity.setBizKey(request.getBizKey());
        entity.setDataJson(request.getDataJson());
        entity.setSubmitter(submitter);
        entity.setStatus(STATUS_NORMAL);
        formDataMapper.insert(entity);
        return toVo(entity);
    }

    @Override
    public FormDataVO getDetail(Long id) {
        return toVo(requireData(id));
    }

    @Override
    public PageResult<FormDataVO> page(FormDataQueryRequest request) {
        LambdaQueryWrapper<FormData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getDefinitionCode()), FormData::getDefinitionCode,
                request.getDefinitionCode());
        wrapper.eq(StringUtils.hasText(request.getBizKey()), FormData::getBizKey, request.getBizKey());
        wrapper.eq(StringUtils.hasText(request.getSubmitter()), FormData::getSubmitter, request.getSubmitter());
        wrapper.orderByDesc(FormData::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<FormData> page = formDataMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<FormDataVO> records = page.getRecords().stream().map(FormDataServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireData(id);
        formDataMapper.deleteById(id);
    }

    /**
     * 查询表单数据，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 表单数据ID
     * @return 表单数据实体
     */
    private FormData requireData(Long id) {
        FormData entity = formDataMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    /**
     * 校验表单数据长度不超过 64KB
     *
     * @param dataJson 表单数据JSON
     * @throws BizException 超长时抛出 {@code PARAM_ERROR}
     */
    private static void validateDataSize(String dataJson) {
        if (dataJson != null && dataJson.length() > MAX_DATA_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "表单数据长度不能超过 65536 字符");
        }
    }

    /**
     * 表单数据实体转视图对象
     *
     * @param entity 表单数据实体
     * @return 表单数据视图对象
     */
    private static FormDataVO toVo(FormData entity) {
        FormDataVO vo = new FormDataVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
