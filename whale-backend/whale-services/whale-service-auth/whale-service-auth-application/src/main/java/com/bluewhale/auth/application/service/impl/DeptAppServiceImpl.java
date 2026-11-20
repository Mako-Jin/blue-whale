package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.SaveDeptCommand;
import com.bluewhale.auth.application.model.vo.DeptLeaderVO;
import com.bluewhale.auth.application.model.vo.DeptVO;
import com.bluewhale.auth.application.service.DeptAppService;
import com.bluewhale.auth.domain.aggregate.DeptAggregate;
import com.bluewhale.auth.domain.repository.DeptRepository;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.DeptDomainEvent;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@Service
public class DeptAppServiceImpl implements DeptAppService {

    /** Dept 聚合仓储 */
    private final DeptRepository deptRepository;

    /** 领域事件发布端口（供审计等下游消费） */
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 构造器注入
     *
     * @param deptRepository 部门聚合仓储
     * @param domainEventPublisher 领域事件发布端口
     */
    public DeptAppServiceImpl(DeptRepository deptRepository, DomainEventPublisher domainEventPublisher) {
        this.deptRepository = deptRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 查询全量部门树
     *
     * @return 顶级部门节点列表（各节点含递归子节点）
     */
    public List<DeptVO> tree() {
        List<DeptVO> all = deptRepository.findAll().stream().map(DeptVO::from).toList();
        return buildChildren(all, DeptAggregate.ROOT_PARENT_ID);
    }

    /**
     * 查询部门详情
     *
     * @param id 部门 ID
     * @return 部门视图对象
     * @throws BizException 部门不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    public DeptVO getDetail(Long id) {
        return deptRepository.findById(id)
                .map(DeptVO::from)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
    }

    /**
     * 新增部门（默认值由 {@link DeptAggregate#create} 负责）
     *
     * @param command 部门新增命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(SaveDeptCommand command) {
        DeptAggregate dept = DeptAggregate.create(TenantContext.requireId(), command.parentId(), command.deptName(),
                command.sort(), command.leader(), command.leaderUserId());
        DeptAggregate persisted = deptRepository.save(dept);
        domainEventPublisher.publish(new DeptDomainEvent(persisted.getId(), ChangeType.CREATED));
    }

    /**
     * 更新部门（PATCH 契约：{@code command} 中为 {@code null} 的字段保持原值，由 {@link DeptAggregate#applyUpdate} 负责）
     *
     * @param id 部门 ID
     * @param command 部门更新命令
     * @throws BizException 部门不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SaveDeptCommand command) {
        DeptAggregate dept = deptRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        dept.applyUpdate(command.parentId(), command.deptName(), command.sort(), command.leader(),
                command.leaderUserId());
        deptRepository.save(dept);
        domainEventPublisher.publish(new DeptDomainEvent(id, ChangeType.UPDATED));
    }

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
    public DeptLeaderVO leader(Long deptId) {
        DeptAggregate dept = deptRepository.findById(deptId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        return DeptLeaderVO.from(dept);
    }

    /**
     * 删除部门（存在子部门时拒绝）
     *
     * @param id 部门 ID
     * @throws BizException 存在子部门时抛出 {@link ErrorCode#PARAM_ERROR}
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (deptRepository.countChildren(id) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "auth.dept.has-children", "存在子部门，不允许删除");
        }
        deptRepository.deleteById(id);
        domainEventPublisher.publish(new DeptDomainEvent(id, ChangeType.DELETED));
    }

    /**
     * 简单递归组树（保持入参顺序，与旧实现一致）
     *
     * @param all 平铺的部门视图列表
     * @param parentId 父部门 ID
     * @return 该父部门下的子节点列表
     */
    private List<DeptVO> buildChildren(List<DeptVO> all, Long parentId) {
        return all.stream()
                .filter(vo -> vo.getParentId() != null && parentId.equals(vo.getParentId()))
                .peek(vo -> vo.setChildren(buildChildren(all, vo.getId())))
                .toList();
    }
}
