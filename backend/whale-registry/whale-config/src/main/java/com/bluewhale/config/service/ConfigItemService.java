package com.bluewhale.config.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.config.dto.ConfigItemCreateRequest;
import com.bluewhale.config.dto.ConfigItemQueryRequest;
import com.bluewhale.config.dto.ConfigItemRollbackRequest;
import com.bluewhale.config.dto.ConfigItemUpdateRequest;
import com.bluewhale.config.dto.OperatorInfo;
import com.bluewhale.config.vo.ConfigItemHistoryVO;
import com.bluewhale.config.vo.ConfigItemVO;

import java.util.List;

/**
 * 配置项服务
 *
 * <p>配置键租户内唯一；值每次变更版本号 +1 并落历史；回滚产生新版本（不覆盖历史）。</p>
 *
 * @author BlueWhale
 */
public interface ConfigItemService {

    /**
     * 分页查询配置项
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<ConfigItemVO> page(ConfigItemQueryRequest request);

    /**
     * 配置项详情
     *
     * @param id 配置项ID
     * @return 配置项视图对象
     */
    ConfigItemVO getDetail(Long id);

    /**
     * 创建配置项（版本号置 1，落 CREATE 历史）
     *
     * @param request  创建请求
     * @param operator 操作人
     * @return 创建后的配置项视图对象
     */
    ConfigItemVO create(ConfigItemCreateRequest request, OperatorInfo operator);

    /**
     * 更新配置项（值变更时版本号 +1 并落 UPDATE 历史；仅改说明/状态不产生版本）
     *
     * @param id       配置项ID
     * @param request  更新请求
     * @param operator 操作人
     * @return 更新后的配置项视图对象
     */
    ConfigItemVO update(Long id, ConfigItemUpdateRequest request, OperatorInfo operator);

    /**
     * 删除配置项（逻辑删除，落 DELETE 历史；键可重建）
     *
     * @param id       配置项ID
     * @param operator 操作人
     */
    void delete(Long id, OperatorInfo operator);

    /**
     * 配置项变更历史（按版本号倒序，最多 200 条）
     *
     * @param id 配置项ID
     * @return 历史列表
     */
    List<ConfigItemHistoryVO> listHistory(Long id);

    /**
     * 回滚配置值到目标版本（产生新版本，落 ROLLBACK 历史）
     *
     * @param id       配置项ID
     * @param request  回滚请求
     * @param operator 操作人
     * @return 回滚后的配置项视图对象
     */
    ConfigItemVO rollback(Long id, ConfigItemRollbackRequest request, OperatorInfo operator);
}
