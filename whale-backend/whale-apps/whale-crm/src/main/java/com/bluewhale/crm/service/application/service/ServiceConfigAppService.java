package com.bluewhale.crm.service.application.service;

import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.service.application.command.ServiceCalendarCommand;
import com.bluewhale.crm.service.application.command.SlaPoliciesCommand;
import com.bluewhale.crm.service.application.view.ServiceCalendarView;
import com.bluewhale.crm.service.application.view.SlaPolicyView;

import java.util.List;

/**
 * 售后服务配置应用服务（SLA 策略与工作日历）。
 *
 * @author BlueWhale
 */
public interface ServiceConfigAppService {

    /**
     * 查询三档 SLA 策略（缺省补默认值）。
     *
     * @param actor 当前操作人
     * @return 策略列表
     */
    List<SlaPolicyView> listPolicies(CrmActor actor);

    /**
     * 保存三档 SLA 策略。
     *
     * @param command 保存命令
     * @param actor 当前操作人
     * @return 最新策略列表
     */
    List<SlaPolicyView> savePolicies(SlaPoliciesCommand command, CrmActor actor);

    /**
     * 查询工作日历（缺省返回默认值）。
     *
     * @param actor 当前操作人
     * @return 日历视图
     */
    ServiceCalendarView getCalendar(CrmActor actor);

    /**
     * 保存工作日历。
     *
     * @param command 保存命令
     * @param actor 当前操作人
     * @return 日历视图
     */
    ServiceCalendarView saveCalendar(ServiceCalendarCommand command, CrmActor actor);
}
