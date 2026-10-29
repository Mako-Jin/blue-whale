package com.bluewhale.oa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.converter.OaConverter;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.dto.OaLeaveQueryRequest;
import com.bluewhale.oa.dto.OaLeaveSaveRequest;
import com.bluewhale.oa.entity.OaLeave;
import com.bluewhale.oa.mapper.OaLeaveMapper;
import com.bluewhale.oa.service.OaLeaveService;
import com.bluewhale.oa.vo.OaLeaveVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * OA 请假申请服务实现
 *
 * <p>状态机：{@code PENDING → APPROVED / REJECTED / CANCELLED}，终态不可回流。
 * 仅 {@code PENDING} 可编辑与删除；单号在租户内唯一（逻辑删除行仍占用，避免复用冲突）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OaLeaveServiceImpl implements OaLeaveService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 状态：待审批 */
    private static final String STATUS_PENDING = "PENDING";

    private final OaLeaveMapper oaLeaveMapper;

    @Override
    public PageResult<OaLeaveVO> page(OaLeaveQueryRequest request) {
        LambdaQueryWrapper<OaLeave> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getCode()), OaLeave::getCode, request.getCode());
        wrapper.eq(StringUtils.hasText(request.getStatus()), OaLeave::getStatus, request.getStatus());
        wrapper.eq(StringUtils.hasText(request.getApplicant()), OaLeave::getApplicant, request.getApplicant());
        wrapper.eq(StringUtils.hasText(request.getLeaveType()), OaLeave::getLeaveType, request.getLeaveType());
        wrapper.orderByDesc(OaLeave::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<OaLeave> page = oaLeaveMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<OaLeaveVO> records = page.getRecords().stream().map(OaLeaveServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public OaLeaveVO getDetail(Long id) {
        return toVo(requireLeave(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaLeaveVO create(OaLeaveSaveRequest request) {
        validateDateRange(request);
        assertCodeUnique(request.getCode(), null);
        OaLeave leave = OaConverter.INSTANCE.toEntity(request);
        leave.setStatus(STATUS_PENDING);
        oaLeaveMapper.insert(leave);
        log.info("请假申请已创建: id={}, code={}", leave.getId(), leave.getCode());
        return toVo(leave);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaLeaveVO update(Long id, OaLeaveSaveRequest request) {
        OaLeave leave = requireEditable(id);
        validateDateRange(request);
        assertCodeUnique(request.getCode(), id);
        OaConverter.INSTANCE.updateEntity(request, leave);
        oaLeaveMapper.updateById(leave);
        log.info("请假申请已更新: id={}, code={}", id, leave.getCode());
        return toVo(leave);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        OaLeave leave = requireLeave(id);
        if (!STATUS_PENDING.equals(leave.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅待审批申请可变更状态");
        }
        leave.setStatus(status);
        oaLeaveMapper.updateById(leave);
        log.info("请假申请状态已变更: id={}, status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireEditable(id);
        oaLeaveMapper.deleteById(id);
        log.info("请假申请已删除: id={}", id);
    }

    /**
     * 按 ID 查询未删除的申请。
     *
     * @param id 主键ID
     * @return 申请实体
     */
    private OaLeave requireLeave(Long id) {
        OaLeave leave = id == null ? null : oaLeaveMapper.selectById(id);
        if (leave == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "请假申请不存在");
        }
        return leave;
    }

    /**
     * 查询并校验申请处于待审批状态。
     *
     * @param id 主键ID
     * @return 申请实体
     */
    private OaLeave requireEditable(Long id) {
        OaLeave leave = requireLeave(id);
        if (!STATUS_PENDING.equals(leave.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅待审批申请可编辑或删除");
        }
        return leave;
    }

    /**
     * 校验日期区间。
     *
     * @param request 保存请求
     */
    private static void validateDateRange(OaLeaveSaveRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "结束日期不能早于开始日期");
        }
    }

    /**
     * 校验申请单号在租户内唯一。
     *
     * @param code      申请单号
     * @param excludeId 更新时排除自身ID，新增传 {@code null}
     */
    private void assertCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<OaLeave> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OaLeave::getCode, code);
        if (excludeId != null) {
            wrapper.ne(OaLeave::getId, excludeId);
        }
        if (oaLeaveMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "申请单号已存在");
        }
    }

    /**
     * 实体转视图对象
     *
     * @param leave 申请实体
     * @return 视图对象
     */
    private static OaLeaveVO toVo(OaLeave leave) {
        return OaConverter.INSTANCE.toLeaveVo(leave);
    }
}
