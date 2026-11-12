package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.ReplaceUserAttrsCommand;
import com.bluewhale.auth.application.model.vo.UserAttrVO;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserPermissionDomainEvent;
import com.bluewhale.auth.domain.permission.aggregate.UserPermissionAttribute;
import com.bluewhale.auth.domain.permission.repository.UserPermissionRepository;

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
public interface UserPermissionAppService {

    /**
     * 查询指定用户的全部 ABAC 属性
     *
     * @param userId 用户 ID
     * @return 用户属性视图列表
     */
    List<UserAttrVO> listByUserId(Long userId);

    /**
     * 全量覆盖用户 ABAC 属性（先删后批量插，空列表只删不插，避免唯一键冲突）
     *
     * @param userId 用户 ID
     * @param command 属性全量覆盖命令；{@code attrs} 为 {@code null} 或空时仅清空
     */
    void replaceUserAttrs(Long userId, ReplaceUserAttrsCommand command);
}
