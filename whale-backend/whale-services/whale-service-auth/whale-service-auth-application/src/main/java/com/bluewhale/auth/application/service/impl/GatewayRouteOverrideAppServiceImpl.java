package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.command.SaveGatewayRouteOverrideCommand;
import com.bluewhale.auth.application.model.vo.GatewayRouteOverrideVO;
import com.bluewhale.auth.application.service.GatewayRouteOverrideAppService;
import com.bluewhale.auth.domain.aggregate.GatewayRouteOverrideAggregate;
import com.bluewhale.auth.domain.repository.GatewayRouteOverrideRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 网关路由业务属性覆盖应用服务实现。
 *
 * <p>下发查询不加事务；管理端写操作按用例加事务。删除为物理删除（主键即路由 id，
 * 删除后恢复路由默认行为）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class GatewayRouteOverrideAppServiceImpl implements GatewayRouteOverrideAppService {

    private final GatewayRouteOverrideRepository routeOverrideRepository;

    /**
     * 查询全部覆盖条目（含停用与窗口外条目）
     *
     * @return 路由覆盖视图对象列表
     */
    @Override
    public List<GatewayRouteOverrideVO> listAll() {
        return routeOverrideRepository.findAll().stream()
                .map(GatewayRouteOverrideVO::from)
                .toList();
    }

    /**
     * 新增路由覆盖
     *
     * @param routeId 路由 ID
     * @param command 保存命令
     * @throws BizException 条目已存在时抛出 {@link ErrorCode#PARAM_ERROR}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(String routeId, SaveGatewayRouteOverrideCommand command) {
        GatewayRouteOverrideAggregate override = GatewayRouteOverrideAggregate.create(routeId,
                toStatus(command.enabled()), command.effectiveFrom(), command.effectiveTo(), command.remark());
        if (routeOverrideRepository.findById(override.getRouteId()).isPresent()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该路由已存在覆盖条目");
        }
        routeOverrideRepository.save(override);
    }

    /**
     * 更新路由覆盖（版本号自增）
     *
     * @param routeId 路由 ID
     * @param command 保存命令
     * @throws BizException 条目不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String routeId, SaveGatewayRouteOverrideCommand command) {
        GatewayRouteOverrideAggregate current = routeOverrideRepository.findById(routeId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        GatewayRouteOverrideAggregate updated = current.applyUpdate(toStatus(command.enabled()),
                command.effectiveFrom(), command.effectiveTo(), command.remark());
        routeOverrideRepository.save(updated);
    }

    /**
     * 布尔启用状态转换为聚合状态值：{@code null} 保持原值，{@code true}=1（启用），{@code false}=0（停用）
     *
     * @param enabled 布尔启用状态
     * @return 聚合状态值
     */
    private Integer toStatus(Boolean enabled) {
        if (enabled == null) {
            return null;
        }
        return enabled ? GatewayRouteOverrideAggregate.STATUS_ENABLED : GatewayRouteOverrideAggregate.STATUS_DISABLED;
    }

    /**
     * 删除路由覆盖（恢复路由默认行为）
     *
     * @param routeId 路由 ID
     * @throws BizException 条目不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String routeId) {
        routeOverrideRepository.findById(routeId).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        routeOverrideRepository.deleteById(routeId);
    }
}
