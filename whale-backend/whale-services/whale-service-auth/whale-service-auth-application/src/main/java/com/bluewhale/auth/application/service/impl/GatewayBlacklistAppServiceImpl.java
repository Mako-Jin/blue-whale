package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.command.SaveGatewayAccessRuleCommand;
import com.bluewhale.auth.application.model.vo.GatewayAccessRuleVO;
import com.bluewhale.auth.application.model.vo.GatewayBlacklistVO;
import com.bluewhale.auth.application.service.GatewayBlacklistAppService;
import com.bluewhale.auth.domain.aggregate.GatewayBlacklistAggregate;
import com.bluewhale.auth.domain.repository.GatewayBlacklistRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 网关黑名单应用服务实现。
 *
 * <p>下发查询不加事务；管理端写操作按用例加事务。删除为物理删除，
 * 避免逻辑删除长期占用 {@code (path_pattern, http_method, scope)} 唯一键。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class GatewayBlacklistAppServiceImpl implements GatewayBlacklistAppService {

    private final GatewayBlacklistRepository gatewayBlacklistRepository;

    /**
     * 查询全部生效的平台级黑名单
     *
     * @return 黑名单视图对象列表
     */
    @Override
    public List<GatewayBlacklistVO> listEnabled() {
        return gatewayBlacklistRepository.findAllEnabled().stream()
                .filter(GatewayBlacklistAggregate::isEnabled)
                .map(GatewayBlacklistVO::from)
                .toList();
    }

    /**
     * 管理端查询黑名单（含已解除项）
     *
     * @param status 状态过滤；{@code null} 表示全部
     * @return 访问名单管理视图对象列表
     */
    @Override
    public List<GatewayAccessRuleVO> list(Integer status) {
        return gatewayBlacklistRepository.findAll(status).stream()
                .map(GatewayAccessRuleVO::from)
                .toList();
    }

    /**
     * 新增黑名单条目
     *
     * @param command 保存命令
     * @throws BizException 同路径 + 方法已存在时抛出 {@link ErrorCode#PARAM_ERROR}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SaveGatewayAccessRuleCommand command) {
        GatewayBlacklistAggregate rule = GatewayBlacklistAggregate.create(command.pathPattern(),
                command.httpMethod(), command.status(), command.sortNo(), command.remark());
        if (gatewayBlacklistRepository.existsRule(rule.getPathPattern(), rule.getHttpMethod(), rule.getScope(),
                null)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "auth.gateway.blacklist-exists", "该路径与方法已存在黑名单条目");
        }
        gatewayBlacklistRepository.save(rule);
    }

    /**
     * 更新黑名单条目
     *
     * @param id 主键
     * @param command 保存命令
     * @throws BizException 条目不存在时抛出 {@link ErrorCode#NOT_FOUND}；与既有条目冲突时抛出
     *     {@link ErrorCode#PARAM_ERROR}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SaveGatewayAccessRuleCommand command) {
        GatewayBlacklistAggregate current = gatewayBlacklistRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        GatewayBlacklistAggregate updated = current.applyUpdate(command.pathPattern(), command.httpMethod(),
                command.status(), command.sortNo(), command.remark());
        if (gatewayBlacklistRepository.existsRule(updated.getPathPattern(), updated.getHttpMethod(),
                updated.getScope(), id)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "auth.gateway.blacklist-exists", "该路径与方法已存在黑名单条目");
        }
        gatewayBlacklistRepository.save(updated);
    }

    /**
     * 删除黑名单条目
     *
     * @param id 主键
     * @throws BizException 条目不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        gatewayBlacklistRepository.findById(id).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        gatewayBlacklistRepository.deleteById(id);
    }
}
