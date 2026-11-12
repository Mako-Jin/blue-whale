package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.vo.TenantMemberVO;
import com.bluewhale.auth.application.vo.TenantPasswordPolicyVO;
import com.bluewhale.auth.application.vo.TenantVO;
import com.bluewhale.auth.interfaces.dto.response.TenantAdminResponse;
import com.bluewhale.auth.interfaces.dto.response.TenantMemberResponse;
import com.bluewhale.auth.interfaces.dto.response.TenantPasswordPolicyResponse;
import com.bluewhale.common.core.result.PageResult;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 租户接口装配器：application 视图对象 → interfaces 响应 DTO。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface TenantAssembler {

    /**
     * 租户视图对象 → 租户管理响应
     *
     * @param vo 租户视图对象
     * @return 租户管理响应
     */
    TenantAdminResponse toResponse(TenantVO vo);

    /**
     * 租户分页 → 租户管理响应分页
     *
     * @param page 租户分页
     * @return 租户管理响应分页
     */
    default PageResult<TenantAdminResponse> toPage(PageResult<TenantVO> page) {
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 租户成员视图对象 → 租户成员响应
     *
     * @param vo 租户成员视图对象
     * @return 租户成员响应
     */
    TenantMemberResponse toMemberResponse(TenantMemberVO vo);

    /**
     * 租户成员角色项 → 租户成员角色项响应
     *
     * @param item 租户成员角色项
     * @return 租户成员角色项响应
     */
    TenantMemberResponse.RoleItemResponse toRoleItemResponse(TenantMemberVO.RoleItem item);

    /**
     * 租户成员视图对象列表 → 租户成员响应列表
     *
     * @param vos 租户成员视图对象列表
     * @return 租户成员响应列表
     */
    List<TenantMemberResponse> toMemberResponses(List<TenantMemberVO> vos);

    /**
     * 租户口令策略视图 → 租户口令策略响应
     *
     * @param vo 租户口令策略视图
     * @return 租户口令策略响应
     */
    TenantPasswordPolicyResponse toPasswordPolicyResponse(TenantPasswordPolicyVO vo);
}
