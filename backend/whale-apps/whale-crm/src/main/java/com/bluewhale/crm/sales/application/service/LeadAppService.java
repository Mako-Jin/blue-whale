package com.bluewhale.crm.sales.application.service;

import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.sales.application.command.LeadActivityCreateCommand;
import com.bluewhale.crm.sales.application.command.LeadAssignCommand;
import com.bluewhale.crm.sales.application.command.LeadConvertCommand;
import com.bluewhale.crm.sales.application.command.LeadInvalidCommand;
import com.bluewhale.crm.sales.application.command.LeadSaveCommand;
import com.bluewhale.crm.sales.application.command.LeadStatusCommand;
import com.bluewhale.crm.sales.application.view.LeadActivityView;
import com.bluewhale.crm.sales.application.view.LeadConvertResultView;
import com.bluewhale.crm.sales.application.view.LeadView;
import com.bluewhale.crm.sales.domain.model.LeadQuery;

import java.util.List;

/**
 * 线索应用服务（M2）。
 *
 * @author BlueWhale
 */
public interface LeadAppService {

    /**
     * 分页查询线索（自动应用数据范围）。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 线索分页
     */
    PageResult<LeadView> page(LeadQuery query, CrmActor actor);

    /**
     * 查询线索详情。
     *
     * @param id 线索 ID
     * @param actor 当前操作人
     * @return 线索视图
     */
    LeadView detail(Long id, CrmActor actor);

    /**
     * 查重（电话 / 邮箱疑似重复）。
     *
     * @param phone 电话
     * @param email 邮箱
     * @return 疑似重复线索
     */
    List<LeadView> duplicateCheck(String phone, String email);

    /**
     * 新建线索。
     *
     * @param command 新建命令
     * @param actor 当前操作人
     * @return 线索视图
     */
    LeadView create(LeadSaveCommand command, CrmActor actor);

    /**
     * 编辑线索。
     *
     * @param id 线索 ID
     * @param command 编辑命令
     * @param actor 当前操作人
     * @return 线索视图
     */
    LeadView update(Long id, LeadSaveCommand command, CrmActor actor);

    /**
     * 推进线索状态。
     *
     * @param id 线索 ID
     * @param command 状态命令
     * @param actor 当前操作人
     * @return 线索视图
     */
    LeadView advance(Long id, LeadStatusCommand command, CrmActor actor);

    /**
     * 分配负责人。
     *
     * @param id 线索 ID
     * @param command 分配命令
     * @param actor 当前操作人
     * @return 线索视图
     */
    LeadView assign(Long id, LeadAssignCommand command, CrmActor actor);

    /**
     * 标记无效。
     *
     * @param id 线索 ID
     * @param command 无效命令
     * @param actor 当前操作人
     * @return 线索视图
     */
    LeadView invalidate(Long id, LeadInvalidCommand command, CrmActor actor);

    /**
     * 线索转化（幂等，已转化返回既有结果）。
     *
     * @param id 线索 ID
     * @param command 转化命令
     * @param actor 当前操作人
     * @return 转化结果
     */
    LeadConvertResultView convert(Long id, LeadConvertCommand command, CrmActor actor);

    /**
     * 分页查询线索活动。
     *
     * @param id 线索 ID
     * @param pageQuery 分页参数
     * @param actor 当前操作人
     * @return 活动分页
     */
    PageResult<LeadActivityView> activityPage(Long id, PageQuery pageQuery, CrmActor actor);

    /**
     * 追加线索活动。
     *
     * @param id 线索 ID
     * @param command 活动命令
     * @param actor 当前操作人
     * @return 活动视图
     */
    LeadActivityView addActivity(Long id, LeadActivityCreateCommand command, CrmActor actor);
}
