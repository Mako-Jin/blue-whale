package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bluewhale.auth.dto.DeptRequest;
import com.bluewhale.auth.entity.SysDept;
import com.bluewhale.auth.mapper.SysDeptMapper;
import com.bluewhale.auth.service.SysDeptService;
import com.bluewhale.auth.vo.DeptVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统部门服务实现
 *
 * @author BlueWhale
 */
@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    @Override
    public List<DeptVO> tree() {
        List<DeptVO> all = list(new LambdaQueryWrapper<SysDept>()
                        .orderByAsc(SysDept::getSort)
                        .orderByAsc(SysDept::getId))
                .stream().map(DeptVO::from).toList();
        return buildChildren(all, 0L);
    }

    /**
     * 简单递归组树
     */
    private List<DeptVO> buildChildren(List<DeptVO> all, Long parentId) {
        return all.stream()
                .filter(vo -> vo.getParentId() != null && parentId.equals(vo.getParentId()))
                .peek(vo -> vo.setChildren(buildChildren(all, vo.getId())))
                .toList();
    }

    @Override
    public DeptVO getDetail(Long id) {
        SysDept dept = getById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return DeptVO.from(dept);
    }

    @Override
    public void create(DeptRequest request) {
        SysDept dept = new SysDept();
        copyProperties(request, dept);
        save(dept);
    }

    @Override
    public void update(Long id, DeptRequest request) {
        SysDept dept = getById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        copyProperties(request, dept);
        updateById(dept);
    }

    @Override
    public void delete(Long id) {
        Long children = baseMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在子部门，不允许删除");
        }
        removeById(id);
    }

    private void copyProperties(DeptRequest request, SysDept dept) {
        dept.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        dept.setDeptName(request.getDeptName());
        dept.setSort(request.getSort() == null ? 0 : request.getSort());
        dept.setLeader(request.getLeader());
    }
}
