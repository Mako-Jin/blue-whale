package com.bluewhale.auth.application.permission;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.permission.command.SavePermissionCommand;
import com.bluewhale.auth.application.permission.query.PermissionPageQuery;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.PermissionDomainEvent;
import com.bluewhale.auth.domain.model.vo.PermCode;
import com.bluewhale.auth.domain.permission.aggregate.Permission;
import com.bluewhale.auth.domain.permission.repository.PermissionRepository;
import com.bluewhale.auth.application.vo.PermissionVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限点应用服务（AUTH-17 / T-DDD-9）
 *
 * <p>承载管理端权限点用例编排：分页 / 全量权限码 / 详情 / 新增 / 更新 / 删除。
 * 依赖仅限 {@link PermissionRepository}、{@link DomainEventPublisher} 与既有 VO/PageResult；
 * 更新、删除后发布领域事件，由基础设施层监听器在事务提交后按权限点失效用户缓存。</p>
 *
 * @author BlueWhale
 */
@Service
public class PermissionAppService {

    /** Permission 聚合仓储 */
    private final PermissionRepository permissionRepository;

    /** 领域事件发布端口（缓存失效等副作用由基础设施层监听） */
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 构造器注入
     *
     * @param permissionRepository 权限点聚合仓储
     * @param domainEventPublisher 领域事件发布端口
     */
    public PermissionAppService(PermissionRepository permissionRepository,
                                DomainEventPublisher domainEventPublisher) {
        this.permissionRepository = permissionRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 分页查询权限点
     *
     * @param query 权限点分页查询条件
     * @return 权限点分页结果
     */
    public PageResult<PermissionVO> page(PermissionPageQuery query) {
        PermissionRepository.PermissionPage page = permissionRepository.page(
                query.current(), query.size(), query.keyword(), query.permType());
        List<PermissionVO> records = page.records().stream().map(PermissionVO::from).toList();
        return PageResult.of(records, page.total(), query.current(), query.size());
    }

    /**
     * 查询全部启用权限码（去重）
     *
     * @return 启用权限码列表
     */
    public List<String> listAllCodes() {
        return permissionRepository.findEnabledAll().stream()
                .map(permission -> permission.getPermCode().value())
                .distinct()
                .toList();
    }

    /**
     * 查询权限点详情
     *
     * @param id 权限点 ID
     * @return 权限点视图对象
     * @throws BizException 权限点不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    public PermissionVO getDetail(Long id) {
        return permissionRepository.findById(id)
                .map(PermissionVO::from)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
    }

    /**
     * 新增权限点（先校验权限码格式与全局唯一性）
     *
     * @param command 权限点新增命令
     * @throws BizException 权限码格式非法或已存在时抛出 {@link ErrorCode#PARAM_ERROR}
     */
    public void create(SavePermissionCommand command) {
        PermCode permCode = PermCode.of(command.permCode());
        if (permissionRepository.existsByPermCode(permCode.value(), null)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "权限编码已存在");
        }
        Permission permission = Permission.create(command.parentId(), permCode, command.permName(),
                command.permType(), command.path(), command.method(), command.sortNo(), command.status());
        permissionRepository.save(permission);
        domainEventPublisher.publish(new PermissionDomainEvent(permission.getId(), ChangeType.CREATED));
    }

    /**
     * 更新权限点（校验权限码唯一性后落库，并发布更新事件）
     *
     * @param id 权限点 ID
     * @param command 权限点更新命令
     * @throws BizException 权限点不存在时抛出 {@link ErrorCode#NOT_FOUND}；权限码已存在时抛出 {@link ErrorCode#PARAM_ERROR}
     */
    public void update(Long id, SavePermissionCommand command) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        PermCode permCode = PermCode.of(command.permCode());
        if (permissionRepository.existsByPermCode(permCode.value(), id)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "权限编码已存在");
        }
        permission.applyChanges(command.parentId(), permCode, command.permName(), command.permType(),
                command.path(), command.method(), command.sortNo(), command.status());
        permissionRepository.save(permission);
        // 权限点变更后发布事件，由监听器在提交后失效持有该点位的用户缓存
        domainEventPublisher.publish(new PermissionDomainEvent(id, ChangeType.UPDATED));
    }

    /**
     * 删除权限点（存在子权限时拒绝，成功后发布删除事件）
     *
     * @param id 权限点 ID
     * @throws BizException 存在子权限时抛出 {@link ErrorCode#PARAM_ERROR}
     */
    public void delete(Long id) {
        if (permissionRepository.countChildren(id) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在子权限，不允许删除");
        }
        permissionRepository.deleteById(id);
        // 权限点删除后发布事件，由监听器在提交后失效持有该点位的用户缓存
        domainEventPublisher.publish(new PermissionDomainEvent(id, ChangeType.DELETED));
    }
}
