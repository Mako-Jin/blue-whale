package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.vo.GatewayBlacklistVO;
import com.bluewhale.auth.application.service.GatewayBlacklistAppService;
import com.bluewhale.auth.domain.aggregate.GatewayBlacklistAggregate;
import com.bluewhale.auth.domain.repository.GatewayBlacklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 网关黑名单应用服务实现：纯查询，不加事务。
 *
 * <p>仓储已在 SQL 侧过滤 {@code status=1}，此处再按聚合不变量兜底一次，
 * 避免持久化层缓存或映射异常把已解除的封禁项下发给网关。</p>
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
}
