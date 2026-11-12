package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.vo.DeptLeaderVO;
import com.bluewhale.auth.application.model.vo.DeptVO;
import com.bluewhale.auth.interfaces.dto.response.DeptLeaderResponse;
import com.bluewhale.auth.interfaces.dto.response.DeptResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 部门接口装配器：application 视图对象 → interfaces 响应 DTO。
 *
 * <p>部门 {@code children} 通过 {@link #toResponse(DeptVO)} 递归映射。</p>
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface DeptAssembler {

    /**
     * 部门视图对象 → 部门响应
     *
     * @param vo 部门视图对象
     * @return 部门响应
     */
    DeptResponse toResponse(DeptVO vo);

    /**
     * 部门视图对象列表 → 部门响应列表
     *
     * @param vos 部门视图对象列表
     * @return 部门响应列表
     */
    List<DeptResponse> toResponses(List<DeptVO> vos);

    /**
     * 部门负责人视图对象 → 部门负责人响应
     *
     * @param vo 部门负责人视图对象
     * @return 部门负责人响应
     */
    DeptLeaderResponse toLeaderResponse(DeptLeaderVO vo);
}
