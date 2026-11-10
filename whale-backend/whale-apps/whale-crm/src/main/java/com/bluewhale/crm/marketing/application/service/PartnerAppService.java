package com.bluewhale.crm.marketing.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.marketing.application.command.PartnerCreateCommand;
import com.bluewhale.crm.marketing.application.command.PartnerReportCreateCommand;
import com.bluewhale.crm.marketing.application.command.PartnerReportResolveCommand;
import com.bluewhale.crm.marketing.application.command.PartnerUpdateCommand;
import com.bluewhale.crm.marketing.application.view.PartnerReportView;
import com.bluewhale.crm.marketing.application.view.PartnerView;
import com.bluewhale.crm.marketing.domain.model.PartnerQuery;
import com.bluewhale.crm.marketing.domain.model.PartnerReportQuery;

/**
 * 渠道伙伴与渠道报单应用服务。
 *
 * @author BlueWhale
 */
public interface PartnerAppService {

    /**
     * 分页查询渠道伙伴。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页结果
     */
    PageResult<PartnerView> pagePartners(PartnerQuery query, CrmActor actor);

    /**
     * 查看渠道伙伴详情。
     *
     * @param id 伙伴 ID
     * @param actor 当前操作人
     * @return 视图
     */
    PartnerView partnerDetail(Long id, CrmActor actor);

    /**
     * 新建渠道伙伴。
     *
     * @param command 新建命令
     * @param actor 当前操作人
     * @return 视图
     */
    PartnerView createPartner(PartnerCreateCommand command, CrmActor actor);

    /**
     * 编辑渠道伙伴。
     *
     * @param id 伙伴 ID
     * @param command 编辑命令
     * @param actor 当前操作人
     * @return 视图
     */
    PartnerView updatePartner(Long id, PartnerUpdateCommand command, CrmActor actor);

    /**
     * 分页查询渠道报单。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页结果
     */
    PageResult<PartnerReportView> pageReports(PartnerReportQuery query, CrmActor actor);

    /**
     * 查看渠道报单详情。
     *
     * @param id 报单 ID
     * @param actor 当前操作人
     * @return 视图
     */
    PartnerReportView reportDetail(Long id, CrmActor actor);

    /**
     * 新建渠道报单（同客户同项目已有有效报单时进入冲突）。
     *
     * @param command 新建命令
     * @param actor 当前操作人
     * @return 视图
     */
    PartnerReportView createReport(PartnerReportCreateCommand command, CrmActor actor);

    /**
     * 裁决冲突报单。
     *
     * @param id 报单 ID
     * @param command 裁决命令
     * @param actor 当前操作人
     * @return 视图
     */
    PartnerReportView resolveReport(Long id, PartnerReportResolveCommand command, CrmActor actor);
}
