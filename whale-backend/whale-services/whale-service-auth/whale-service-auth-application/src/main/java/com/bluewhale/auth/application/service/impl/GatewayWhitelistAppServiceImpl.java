package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.command.SaveGatewayAccessRuleCommand;
import com.bluewhale.auth.application.model.vo.GatewayAccessRuleVO;
import com.bluewhale.auth.application.model.vo.GatewayWhitelistVO;
import com.bluewhale.auth.application.service.GatewayWhitelistAppService;
import com.bluewhale.auth.domain.aggregate.GatewayWhitelistAggregate;
import com.bluewhale.auth.domain.repository.GatewayWhitelistRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 网关白名单应用服务实现。
 *
 * <p>下发查询不加事务；管理端写操作按用例加事务。删除为物理删除，
 * 避免逻辑删除长期占用 {@code (path_pattern, http_method, scope, tenant_id)} 唯一键。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class GatewayWhitelistAppServiceImpl implements GatewayWhitelistAppService {

    private final GatewayWhitelistRepository gatewayWhitelistRepository;

    /**
     * 查询全部启用的白名单（平台级 + 租户级）
     *
     * @return 白名单视图对象列表
     */
    @Override
    public List<GatewayWhitelistVO> listEnabled() {
        return gatewayWhitelistRepository.findAllEnabled().stream()
                .filter(GatewayWhitelistAggregate::isEnabled)
                .map(GatewayWhitelistVO::from)
                .toList();
    }

    /**
     * 管理端查询白名单（含停用项）
     *
     * @param status 状态过滤；{@code null} 表示全部
     * @return 访问名单管理视图对象列表
     */
    @Override
    public List<GatewayAccessRuleVO> list(Integer status) {
        return gatewayWhitelistRepository.findAll(status).stream()
                .map(GatewayAccessRuleVO::from)
                .toList();
    }

    /**
     * 新增白名单条目
     *
     * @param command 保存命令
     * @throws BizException 同路径 + 方法已存在时抛出 {@link ErrorCode#PARAM_ERROR}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SaveGatewayAccessRuleCommand command) {
        GatewayWhitelistAggregate rule = GatewayWhitelistAggregate.create(command.pathPattern(),
                command.httpMethod(), command.scope(), command.tenantId(), command.status(), command.sortNo(),
                command.remark());
        if (gatewayWhitelistRepository.existsRule(rule.getPathPattern(), rule.getHttpMethod(), rule.getScope(),
                rule.getTenantId(), null)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该路径与方法已存在白名单条目");
        }
        gatewayWhitelistRepository.save(rule);
    }

    /**
     * 更新白名单条目
     *
     * @param id 主键
     * @param command 保存命令
     * @throws BizException 条目不存在时抛出 {@link ErrorCode#NOT_FOUND}；与既有条目冲突时抛出
     *     {@link ErrorCode#PARAM_ERROR}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SaveGatewayAccessRuleCommand command) {
        GatewayWhitelistAggregate current = gatewayWhitelistRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        GatewayWhitelistAggregate updated = current.applyUpdate(command.pathPattern(), command.httpMethod(),
                command.status(), command.sortNo(), command.remark());
        if (gatewayWhitelistRepository.existsRule(updated.getPathPattern(), updated.getHttpMethod(),
                updated.getScope(), updated.getTenantId(), id)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该路径与方法已存在白名单条目");
        }
        gatewayWhitelistRepository.save(updated);
    }

    /**
     * 删除白名单条目
     *
     * @param id 主键
     * @throws BizException 条目不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        gatewayWhitelistRepository.findById(id).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        gatewayWhitelistRepository.deleteById(id);
    }
}
