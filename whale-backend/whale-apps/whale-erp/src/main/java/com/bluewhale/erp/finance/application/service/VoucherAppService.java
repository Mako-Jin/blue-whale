package com.bluewhale.erp.finance.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.finance.application.command.VoucherCreateCommand;
import com.bluewhale.erp.finance.application.view.VoucherView;
import com.bluewhale.erp.finance.domain.model.VoucherQuery;

/**
 * 凭证应用服务。
 *
 * @author BlueWhale
 */
public interface VoucherAppService {

    /**
     * 分页查询凭证。
     *
     * @param query 查询条件
     * @return 凭证分页（不含分录）
     */
    PageResult<VoucherView> page(VoucherQuery query);

    /**
     * 查询凭证详情（含分录）。
     *
     * @param id 凭证 ID
     * @return 凭证视图
     */
    VoucherView detail(Long id);

    /**
     * 创建草稿凭证。
     *
     * @param command 创建命令
     * @return 凭证视图
     */
    VoucherView create(VoucherCreateCommand command);

    /**
     * 记账。
     *
     * @param id 凭证 ID
     * @param userId 记账人 ID
     * @return 凭证视图
     */
    VoucherView post(Long id, Long userId);

    /**
     * 红冲：生成一张反向凭证并标记原凭证。
     *
     * @param id 原凭证 ID
     * @param userId 操作人 ID
     * @return 红冲凭证视图
     */
    VoucherView reverse(Long id, Long userId);
}
