package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.SaveDeptCommand;
import com.bluewhale.auth.application.model.vo.DeptLeaderVO;
import com.bluewhale.auth.application.model.vo.DeptVO;
import com.bluewhale.auth.domain.dept.aggregate.Dept;
import com.bluewhale.auth.domain.dept.repository.DeptRepository;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.DeptDomainEvent;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import java.util.List;

/**
 * 部门应用服务（AUTH-17 / T-DDD-9）
 *
 * <p>承载管理端部门用例编排：部门树 / 详情 / 新增 / 更新 / 删除。依赖仅限 {@link DeptRepository}、
 * {@link DomainEventPublisher} 与既有 VO，返回 VO 而非领域对象；树组装语义与旧
 * {@code SysDeptServiceImpl} 逐行等价。</p>
 *
 * @author BlueWhale
 */
public interface DeptAppService {

    /**
     * 查询全量部门树
     *
     * @return 顶级部门节点列表（各节点含递归子节点）
     */
    public List<DeptVO> tree();

    /**
     * 查询部门详情
     *
     * @param id 部门 ID
     * @return 部门视图对象
     * @throws BizException 部门不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    public DeptVO getDetail(Long id);

    /**
     * 新增部门（默认值由 {@link Dept#create} 负责）
     *
     * @param command 部门新增命令
     */
    public void create(SaveDeptCommand command);

    /**
     * 更新部门（默认值由 {@link Dept#applyChanges} 负责）
     *
     * @param id 部门 ID
     * @param command 部门更新命令
     * @throws BizException 部门不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    public void update(Long id, SaveDeptCommand command);

    /**
     * 解析部门负责人（供 OA 审批中心内网直连解析「部门经理」）
     *
     * <p>{@code leaderUserId} 未维护时仍正常返回，仅将该字段置空，由调用方决定降级策略；
     * 部门不存在或跨租户时抛 {@link ErrorCode#NOT_FOUND}，对应 HTTP 404。</p>
     *
     * @param deptId 部门 ID
     * @return 部门负责人视图对象
     * @throws BizException 部门不存在或跨租户时抛出 {@link ErrorCode#NOT_FOUND}
     */
    public DeptLeaderVO leader(Long deptId);

    /**
     * 删除部门（存在子部门时拒绝）
     *
     * @param id 部门 ID
     * @throws BizException 存在子部门时抛出 {@link ErrorCode#PARAM_ERROR}
     */
    public void delete(Long id);
}
