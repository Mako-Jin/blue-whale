package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.SavePermissionCommand;
import com.bluewhale.auth.application.model.query.PermissionPageQuery;
import com.bluewhale.auth.application.model.vo.PermissionVO;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.PermissionDomainEvent;
import com.bluewhale.auth.domain.model.vo.PermCode;
import com.bluewhale.auth.domain.permission.aggregate.Permission;
import com.bluewhale.auth.domain.permission.repository.PermissionRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;

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
public interface PermissionAppService {

    /**
     * 分页查询权限点
     *
     * @param query 权限点分页查询条件
     * @return 权限点分页结果
     */
    PageResult<PermissionVO> page(PermissionPageQuery query);

    /**
     * 查询全部启用权限码（去重）
     *
     * @return 启用权限码列表
     */
    List<String> listAllCodes();

    /**
     * 查询权限点详情
     *
     * @param id 权限点 ID
     * @return 权限点视图对象
     * @throws BizException 权限点不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    PermissionVO getDetail(Long id);

    /**
     * 新增权限点（先校验权限码格式与全局唯一性）
     *
     * @param command 权限点新增命令
     * @throws BizException 权限码格式非法或已存在时抛出 {@link ErrorCode#PARAM_ERROR}
     */
    void create(SavePermissionCommand command);

    /**
     * 更新权限点（校验权限码唯一性后落库，并发布更新事件）
     *
     * @param id 权限点 ID
     * @param command 权限点更新命令
     * @throws BizException 权限点不存在时抛出 {@link ErrorCode#NOT_FOUND}；权限码已存在时抛出 {@link ErrorCode#PARAM_ERROR}
     */
    void update(Long id, SavePermissionCommand command);

    /**
     * 删除权限点（存在子权限时拒绝，成功后发布删除事件）
     *
     * @param id 权限点 ID
     * @throws BizException 存在子权限时抛出 {@link ErrorCode#PARAM_ERROR}
     */
    void delete(Long id);
}
