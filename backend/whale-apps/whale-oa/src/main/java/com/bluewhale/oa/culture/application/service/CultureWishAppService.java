package com.bluewhale.oa.culture.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.culture.application.command.CultureCommands;
import com.bluewhale.oa.culture.application.view.CultureViews;
import com.bluewhale.oa.culture.domain.model.CultureQuery;

import java.time.LocalDate;
import java.util.List;

/**
 * 生日 / 入职周年祝福应用服务。
 *
 * @author BlueWhale
 */
public interface CultureWishAppService {

    /**
     * 祝福分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CultureViews.Wish> page(CultureQuery query);

    /**
     * 查询指定日期的生日 / 入职周年。
     *
     * @param date 日期
     * @return 今日生日 / 周年
     */
    List<CultureViews.Birthday> listCelebrations(LocalDate date);

    /**
     * 送祝福。
     *
     * @param command 祝福命令
     * @param senderId 发送人用户ID
     * @param senderName 发送人姓名
     * @return 祝福
     */
    CultureViews.Wish send(CultureCommands.WishSave command, Long senderId, String senderName);
}
