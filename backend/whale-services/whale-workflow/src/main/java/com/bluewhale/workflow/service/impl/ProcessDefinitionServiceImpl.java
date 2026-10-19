package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.ProcessDefinitionSaveRequest;
import com.bluewhale.workflow.dto.ProcessQueryRequest;
import com.bluewhale.workflow.entity.ProcessDefinition;
import com.bluewhale.workflow.mapper.ProcessDefinitionMapper;
import com.bluewhale.workflow.service.ProcessDefinitionService;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 流程定义服务实现
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ProcessDefinitionServiceImpl implements ProcessDefinitionService {

    /** 流程状态：正常 */
    private static final int STATUS_NORMAL = 1;

    /** 流程版本号默认值 */
    private static final int DEFAULT_VERSION = 1;

    /** 流程定义 JSON 最大长度（字符数）：64KB */
    private static final int MAX_DEFINITION_LENGTH = 65536;

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final ProcessDefinitionMapper processDefinitionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessDefinitionVO create(ProcessDefinitionSaveRequest request) {
        assertCodeUnique(request.getCode(), null);
        validateDefinitionSize(request.getDefinitionJson());
        ProcessDefinition entity = new ProcessDefinition();
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setDefinitionJson(request.getDefinitionJson());
        entity.setVersion(request.getVersion() == null ? DEFAULT_VERSION : request.getVersion());
        entity.setStatus(request.getStatus() == null ? STATUS_NORMAL : request.getStatus());
        entity.setRemark(request.getRemark());
        processDefinitionMapper.insert(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessDefinitionVO update(Long id, ProcessDefinitionSaveRequest request) {
        ProcessDefinition entity = requireProcess(id);
        if (!entity.getCode().equals(request.getCode())) {
            assertCodeUnique(request.getCode(), id);
            entity.setCode(request.getCode());
        }
        validateDefinitionSize(request.getDefinitionJson());
        entity.setName(request.getName());
        entity.setDefinitionJson(request.getDefinitionJson());
        if (request.getVersion() != null) {
            entity.setVersion(request.getVersion());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        entity.setRemark(request.getRemark());
        processDefinitionMapper.updateById(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireProcess(id);
        processDefinitionMapper.deleteById(id);
    }

    @Override
    public ProcessDefinitionVO getDetail(Long id) {
        return toVo(requireProcess(id));
    }

    @Override
    public PageResult<ProcessDefinitionVO> page(ProcessQueryRequest request) {
        LambdaQueryWrapper<ProcessDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getName()), ProcessDefinition::getName, request.getName());
        wrapper.eq(StringUtils.hasText(request.getCode()), ProcessDefinition::getCode, request.getCode());
        wrapper.eq(request.getStatus() != null, ProcessDefinition::getStatus, request.getStatus());
        wrapper.orderByDesc(ProcessDefinition::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<ProcessDefinition> page = processDefinitionMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<ProcessDefinitionVO> records = page.getRecords().stream().map(ProcessDefinitionServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 查询流程定义，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 流程定义ID
     * @return 流程定义实体
     */
    private ProcessDefinition requireProcess(Long id) {
        ProcessDefinition entity = processDefinitionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    /**
     * 校验流程编码唯一性：命中已存在的编码则抛参数错误
     *
     * @param code 流程编码
     * @param excludeId 需排除的当前记录ID（更新时传入，新增传 {@code null}）
     */
    private void assertCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<ProcessDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDefinition::getCode, code);
        wrapper.ne(excludeId != null, ProcessDefinition::getId, excludeId);
        Long count = processDefinitionMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程编码已存在");
        }
    }

    /**
     * 校验流程定义长度不超过 64KB
     *
     * @param definitionJson 流程定义JSON
     * @throws BizException 超长时抛出 {@code PARAM_ERROR}
     */
    private static void validateDefinitionSize(String definitionJson) {
        if (definitionJson != null && definitionJson.length() > MAX_DEFINITION_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程定义长度不能超过 65536 字符");
        }
    }

    /**
     * 流程实体转视图对象
     *
     * @param entity 流程定义实体
     * @return 流程定义视图对象
     */
    private static ProcessDefinitionVO toVo(ProcessDefinition entity) {
        ProcessDefinitionVO vo = new ProcessDefinitionVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
