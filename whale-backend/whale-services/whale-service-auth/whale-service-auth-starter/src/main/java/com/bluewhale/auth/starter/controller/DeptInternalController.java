package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.service.DeptAppService;
import com.bluewhale.auth.interfaces.api.DeptInternalApi;
import com.bluewhale.auth.interfaces.dto.response.DeptLeaderResponse;
import com.bluewhale.auth.interfaces.dto.response.DeptResponse;
import com.bluewhale.auth.starter.assemble.DeptAssembler;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门内网接口实现（Web 适配层）：仅供内网直连调用，不校验业务权限码。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class DeptInternalController implements DeptInternalApi {

    private final DeptAppService deptAppService;

    private final DeptAssembler deptAssembler;

    /**
     * 查询当前租户全量部门树
     *
     * @return 部门树节点列表
     */
    @Override
    public Result<List<DeptResponse>> tree() {
        return Result.ok(deptAssembler.toResponses(deptAppService.tree()));
    }

    /**
     * 解析指定部门的负责人
     *
     * @param id 部门 ID
     * @return 部门负责人响应
     */
    @Override
    public Result<DeptLeaderResponse> leader(@PathVariable Long id) {
        return Result.ok(deptAssembler.toLeaderResponse(deptAppService.leader(id)));
    }
}
