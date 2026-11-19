package com.bluewhale.scene.application.service.impl;

import com.bluewhale.scene.application.port.ManufacturingBoardPort;
import com.bluewhale.scene.application.service.SceneBoardAppService;
import com.bluewhale.scene.application.view.MfgBoardView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 3D 看板制造数据服务实现。
 *
 * <p>ERP 不可达时返回 {@code degraded=true} 的空视图：宁可让页面提示「数据源不可用」，
 * 也不返回 0 冒充真实产量。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SceneBoardAppServiceImpl implements SceneBoardAppService {

    /** 默认每类记录上限 */
    private static final int DEFAULT_LIMIT = 50;

    /** 每类记录上限（与 ERP 分页上限一致） */
    private static final int MAX_LIMIT = 200;

    private final ManufacturingBoardPort manufacturingBoardPort;

    @Override
    public MfgBoardView manufacturingBoard(Long tenantId, Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        Optional<MfgBoardView> data = manufacturingBoardPort.fetch(tenantId, size);
        if (data.isEmpty()) {
            MfgBoardView degraded = new MfgBoardView();
            degraded.setDegraded(true);
            degraded.setMessage("制造数据源不可用（ERP 未响应或未授权），已降级展示，未显示产量数字");
            return degraded;
        }
        MfgBoardView view = data.get();
        view.setDegraded(false);
        view.setMessage(null);
        return view;
    }
}
