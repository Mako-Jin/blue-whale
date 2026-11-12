package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.SaveBrandConfigCommand;
import com.bluewhale.auth.application.model.query.BrandPageQuery;
import com.bluewhale.auth.application.model.query.BrandResolveQuery;
import com.bluewhale.auth.application.model.vo.BrandConfigVO;
import com.bluewhale.common.core.result.PageResult;

/**
 * 品牌配置应用服务（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
public interface BrandAppService {

    /**
     * 登录页品牌解析（免登录）：按「租户+域名 → 租户默认 → 平台默认」命中启用品牌
     *
     * @param query 解析查询
     * @return 品牌视图对象
     */
    BrandConfigVO resolve(BrandResolveQuery query);

    /**
     * 分页查询品牌配置
     *
     * @param query 分页查询
     * @return 品牌分页结果
     */
    PageResult<BrandConfigVO> page(BrandPageQuery query);

    /**
     * 新增品牌配置
     *
     * @param command 保存命令
     * @return 新建品牌视图对象
     */
    BrandConfigVO create(SaveBrandConfigCommand command);

    /**
     * 编辑品牌配置
     *
     * @param id 主键
     * @param command 保存命令
     * @return 更新后的品牌视图对象
     */
    BrandConfigVO update(Long id, SaveBrandConfigCommand command);

    /**
     * 删除品牌配置（逻辑删除）
     *
     * @param id 主键
     */
    void delete(Long id);
}
