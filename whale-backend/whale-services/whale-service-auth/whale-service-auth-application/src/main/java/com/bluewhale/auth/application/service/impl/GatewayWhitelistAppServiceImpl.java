package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.vo.GatewayWhitelistVO;
import com.bluewhale.auth.application.service.GatewayWhitelistAppService;
import com.bluewhale.auth.domain.aggregate.GatewayWhitelistAggregate;
import com.bluewhale.auth.domain.repository.GatewayWhitelistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 网关白名单应用服务实现：纯查询，不加事务。
 *
 * <p>仓储已在 SQL 侧过滤 {@code status=1}，此处再按聚合不变量兜底一次，
 * 避免持久化层缓存或映射异常把停用项下发给网关。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class GatewayWhitelistAppServiceImpl implements GatewayWhitelistAppService {

    private final GatewayWhitelistRepository gatewayWhitelistRepository;

    /**
     * 查询全部启用的平台级白名单
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
}
