package com.bluewhale.auth.application.userpermission;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.userpermission.command.ReplaceUserAttrsCommand;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserPermissionDomainEvent;
import com.bluewhale.auth.domain.permission.aggregate.UserPermissionAttribute;
import com.bluewhale.auth.domain.permission.repository.UserPermissionRepository;
import com.bluewhale.auth.application.vo.UserAttrVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户 ABAC 属性应用服务（AUTH-17 / T-DDD-9）
 *
 * <p>承载「用户 + 权限点」属性键值对的查询与全量覆盖用例。依赖仅限
 * {@link UserPermissionRepository}、{@link DomainEventPublisher} 与既有 VO；全量覆盖为多写操作，
 * 事务边界放在本层，完成后发布属性覆盖事件。</p>
 *
 * @author BlueWhale
 */
@Service
public class UserPermissionAppService {

    /** 用户 ABAC 属性聚合仓储 */
    private final UserPermissionRepository userPermissionRepository;

    /** 领域事件发布端口（供审计等下游消费） */
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 构造器注入
     *
     * @param userPermissionRepository 用户 ABAC 属性聚合仓储
     * @param domainEventPublisher 领域事件发布端口
     */
    public UserPermissionAppService(UserPermissionRepository userPermissionRepository,
                                    DomainEventPublisher domainEventPublisher) {
        this.userPermissionRepository = userPermissionRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 查询指定用户的全部 ABAC 属性
     *
     * @param userId 用户 ID
     * @return 用户属性视图列表
     */
    public List<UserAttrVO> listByUserId(Long userId) {
        return userPermissionRepository.listByUserId(userId).stream().map(UserAttrVO::from).toList();
    }

    /**
     * 全量覆盖用户 ABAC 属性（先删后批量插，空列表只删不插，避免唯一键冲突）
     *
     * @param userId 用户 ID
     * @param command 属性全量覆盖命令；{@code attrs} 为 {@code null} 或空时仅清空
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceUserAttrs(Long userId, ReplaceUserAttrsCommand command) {
        List<ReplaceUserAttrsCommand.UserAttrCommand> attrs = command.attrs();
        List<UserPermissionAttribute> attributes = attrs == null || attrs.isEmpty()
                ? List.of()
                : attrs.stream()
                        .map(attr -> UserPermissionAttribute.create(userId, attr.permissionId(),
                                attr.attrKey(), attr.attrValue()))
                        .toList();
        userPermissionRepository.replaceAll(userId, attributes);
        domainEventPublisher.publish(new UserPermissionDomainEvent(userId, ChangeType.ATTRS_REPLACED));
    }
}
