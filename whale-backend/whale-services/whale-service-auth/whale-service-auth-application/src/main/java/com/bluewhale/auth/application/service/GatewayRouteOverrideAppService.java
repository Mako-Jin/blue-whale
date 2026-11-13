package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.SaveGatewayRouteOverrideCommand;
import com.bluewhale.auth.application.model.vo.GatewayRouteOverrideVO;

import java.util.List;

/**
 * 网关路由业务属性覆盖应用服务：管理端 CRUD + 网关快照查询。
 *
 * @author BlueWhale
 */
public interface GatewayRouteOverrideAppService {

    /**
     * 查询全部覆盖条目（含停用与窗口外条目，网关按当前时间自行判定生效）
     *
     * @return 路由覆盖视图对象列表，按路由 ID 升序
     */
    List<GatewayRouteOverrideVO> listAll();

    /**
     * 新增路由覆盖
     *
     * @param routeId  路由 ID（与 Nacos/兜底路由 id 逐字一致）
     * @param command 保存命令
     * @throws com.bluewhale.common.core.exception.BizException 条目已存在时抛出
     */
    void create(String routeId, SaveGatewayRouteOverrideCommand command);

    /**
     * 更新路由覆盖（版本号自增）
     *
     * @param routeId 路由 ID
     * @param command 保存命令
     * @throws com.bluewhale.common.core.exception.BizException 条目不存在时抛出
     */
    void update(String routeId, SaveGatewayRouteOverrideCommand command);

    /**
     * 删除路由覆盖（恢复路由默认行为）
     *
     * @param routeId 路由 ID
     * @throws com.bluewhale.common.core.exception.BizException 条目不存在时抛出
     */
    void delete(String routeId);
}
