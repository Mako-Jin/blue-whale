package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.response.DeptLeaderResponse;
import com.bluewhale.auth.interfaces.dto.response.DeptResponse;
import com.bluewhale.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 部门内网 API 契约：供 OA 审批中心等服务经内网直连解析部门树与「部门经理」。
 *
 * <p>路径以 {@code /dept/v1/internal} 开头，网关收口外部访问；本契约不声明业务权限点，
 * 仅限内网可信调用。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/dept/v{version}/internal", version = "1")
public interface DeptInternalApi {

    /**
     * 查询当前租户全量部门树
     *
     * @return 部门树节点列表
     */
    @GetMapping("/tree")
    Result<List<DeptResponse>> tree();

    /**
     * 解析指定部门的负责人
     *
     * @param id 部门 ID
     * @return 部门负责人响应（{@code leaderUserId} 未维护时为空，不视为错误）
     */
    @GetMapping("/{id}/leader")
    Result<DeptLeaderResponse> leader(@PathVariable Long id);
}
