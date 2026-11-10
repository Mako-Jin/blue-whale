package com.bluewhale.auth.controller;

import com.bluewhale.auth.application.dept.DeptAppService;
import com.bluewhale.auth.vo.DeptLeaderVO;
import com.bluewhale.auth.vo.DeptVO;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门内网接口：供 OA 审批中心等服务经内网直连解析部门树与「部门经理」。
 *
 * <p>路径固定以 {@code /dept/internal} 开头，已列入网关
 * {@code whale.gateway.internal-deny.patterns}，经网关的外部访问一律 403；
 * 调用方须经 Nacos 服务发现直连本服务。本接口不校验业务权限码，仅限内网可信调用。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/dept/internal")
@RequiredArgsConstructor
public class DeptInternalController {

    /** 部门应用服务 */
    private final DeptAppService deptAppService;

    /**
     * 查询当前租户全量部门树
     *
     * @return 部门树节点列表
     */
    @GetMapping("/tree")
    public Result<List<DeptVO>> tree() {
        return Result.ok(deptAppService.tree());
    }

    /**
     * 解析指定部门的负责人
     *
     * @param id 部门 ID
     * @return 部门负责人视图对象（{@code leaderUserId} 未维护时为空，不视为错误）
     */
    @GetMapping("/{id}/leader")
    public Result<DeptLeaderVO> leader(@PathVariable Long id) {
        return Result.ok(deptAppService.leader(id));
    }
}
