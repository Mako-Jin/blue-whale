package com.bluewhale.mall.catalog.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.catalog.application.command.MemberSaveCommand;
import com.bluewhale.mall.catalog.application.view.MemberView;
import com.bluewhale.mall.common.MallActor;

/**
 * 商城会员应用服务。
 *
 * <p>会员登记是会员价（{@code PriceType.MEMBER}）的唯一生效依据；
 * 下单计价自动判定，无需买家任何操作。</p>
 *
 * @author BlueWhale
 */
public interface MemberAppService {

    /**
     * 会员分页。
     *
     * @param keyword 会员号/用户 ID 关键字
     * @param status  状态
     * @param current 页码
     * @param size    每页条数
     * @param actor   操作人
     * @return 分页结果
     */
    PageResult<MemberView> page(String keyword, String status, Long current, Long size, MallActor actor);

    /**
     * 会员详情。
     *
     * @param id    主键
     * @param actor 操作人
     * @return 详情
     */
    MemberView detail(Long id, MallActor actor);

    /**
     * 登记会员（按用户 ID upsert：已登记则更新）。
     *
     * @param command 保存命令
     * @param actor   操作人
     * @return 主键
     */
    Long save(MemberSaveCommand command, MallActor actor);

    /**
     * 删除会员登记。
     *
     * @param id    主键
     * @param actor 操作人
     */
    void remove(Long id, MallActor actor);

    /**
     * 用户在指定时刻是否享受会员价（计价入口）。
     *
     * @param userId 平台用户 ID
     * @param now    当前时间
     * @return 生效返回 true
     */
    boolean isMember(Long userId, java.time.LocalDateTime now);
}
