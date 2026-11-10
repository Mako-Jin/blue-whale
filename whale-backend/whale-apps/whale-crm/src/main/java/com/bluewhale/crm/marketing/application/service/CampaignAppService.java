package com.bluewhale.crm.marketing.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.marketing.application.command.AudienceLockCommand;
import com.bluewhale.crm.marketing.application.command.AudiencePreviewCommand;
import com.bluewhale.crm.marketing.application.command.CampaignCreateCommand;
import com.bluewhale.crm.marketing.application.command.CampaignRoiCommand;
import com.bluewhale.crm.marketing.application.command.CampaignStatusCommand;
import com.bluewhale.crm.marketing.application.command.CampaignUpdateCommand;
import com.bluewhale.crm.marketing.application.view.AudiencePreviewView;
import com.bluewhale.crm.marketing.application.view.CampaignAudienceView;
import com.bluewhale.crm.marketing.application.view.CampaignView;
import com.bluewhale.crm.marketing.domain.model.CampaignQuery;

/**
 * 营销活动应用服务。
 *
 * @author BlueWhale
 */
public interface CampaignAppService {

    /**
     * 分页查询营销活动。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页结果
     */
    PageResult<CampaignView> page(CampaignQuery query, CrmActor actor);

    /**
     * 查看活动详情。
     *
     * @param id 活动 ID
     * @param actor 当前操作人
     * @return 视图
     */
    CampaignView detail(Long id, CrmActor actor);

    /**
     * 新建营销活动。
     *
     * @param command 新建命令
     * @param actor 当前操作人
     * @return 视图
     */
    CampaignView create(CampaignCreateCommand command, CrmActor actor);

    /**
     * 编辑营销活动。
     *
     * @param id 活动 ID
     * @param command 编辑命令
     * @param actor 当前操作人
     * @return 视图
     */
    CampaignView update(Long id, CampaignUpdateCommand command, CrmActor actor);

    /**
     * 变更活动状态。
     *
     * @param id 活动 ID
     * @param command 状态命令
     * @param actor 当前操作人
     * @return 视图
     */
    CampaignView changeStatus(Long id, CampaignStatusCommand command, CrmActor actor);

    /**
     * 回填归因数据并复算 ROI。
     *
     * @param id 活动 ID
     * @param command ROI 命令
     * @param actor 当前操作人
     * @return 视图
     */
    CampaignView updateRoi(Long id, CampaignRoiCommand command, CrmActor actor);

    /**
     * 预览活动人群。
     *
     * @param id 活动 ID
     * @param command 预览命令
     * @param actor 当前操作人
     * @return 预览视图
     */
    AudiencePreviewView previewAudience(Long id, AudiencePreviewCommand command, CrmActor actor);

    /**
     * 锁定活动人群快照。
     *
     * @param id 活动 ID
     * @param command 锁定命令
     * @param actor 当前操作人
     * @return 人群快照视图
     */
    CampaignAudienceView lockAudience(Long id, AudienceLockCommand command, CrmActor actor);
}
