package com.bluewhale.crm.service.application.view;

import com.bluewhale.crm.service.domain.model.Ticket;
import com.bluewhale.crm.service.domain.model.TicketAction;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单视图。
 *
 * @author BlueWhale
 */
@Data
public class TicketView {

    /** 工单 ID */
    private Long id;

    /** 工单号 */
    private String ticketNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码 */
    private String customerCode;

    /** 客户名称 */
    private String customerName;

    /** 联系人 ID */
    private Long contactId;

    /** 联系人姓名 */
    private String contactName;

    /** 合同台账 ID */
    private Long contractId;

    /** 商务订单 ID */
    private Long orderId;

    /** ERP 销售订单号 */
    private String erpOrderNo;

    /** 产品编码 */
    private String productCode;

    /** 序列号 */
    private String serialNo;

    /** 问题分类 */
    private String category;

    /** 优先级 */
    private String priority;

    /** 标题 */
    private String title;

    /** 问题描述 */
    private String description;

    /** 状态 */
    private String status;

    /** 处理人 ID */
    private Long assigneeId;

    /** 处理人姓名 */
    private String assigneeName;

    /** 负责人 ID */
    private Long ownerId;

    /** 部门 ID */
    private Long deptId;

    /** 响应截止时间 */
    private LocalDateTime responseDeadline;

    /** 解决截止时间 */
    private LocalDateTime resolveDeadline;

    /** 首次响应时间 */
    private LocalDateTime firstRespondedAt;

    /** 累计暂停秒数 */
    private Long pausedSeconds;

    /** 解决时间 */
    private LocalDateTime resolvedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 解决方案 */
    private String solution;

    /** 重开次数 */
    private Integer reopenCount;

    /** 响应是否逾期 */
    private Boolean responseOverdue;

    /** 解决是否逾期 */
    private Boolean resolveOverdue;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 处理时间线（仅详情返回） */
    private List<TicketActionView> actions;

    /**
     * 由聚合构建视图。
     *
     * @param ticket 工单聚合
     * @param actions 处理记录，可为 null
     * @param now 当前时间
     * @return 视图
     */
    public static TicketView from(Ticket ticket, List<TicketAction> actions, LocalDateTime now) {
        TicketView view = new TicketView();
        view.setId(ticket.getId());
        view.setTicketNo(ticket.getTicketNo());
        view.setCustomerId(ticket.getCustomerId());
        view.setCustomerCode(ticket.getCustomerCode());
        view.setCustomerName(ticket.getCustomerName());
        view.setContactId(ticket.getContactId());
        view.setContactName(ticket.getContactName());
        view.setContractId(ticket.getContractId());
        view.setOrderId(ticket.getOrderId());
        view.setErpOrderNo(ticket.getErpOrderNo());
        view.setProductCode(ticket.getProductCode());
        view.setSerialNo(ticket.getSerialNo());
        view.setCategory(ticket.getCategory());
        view.setPriority(ticket.getPriority() == null ? null : ticket.getPriority().name());
        view.setTitle(ticket.getTitle());
        view.setDescription(ticket.getDescription());
        view.setStatus(ticket.getStatus() == null ? null : ticket.getStatus().name());
        view.setAssigneeId(ticket.getAssigneeId());
        view.setAssigneeName(ticket.getAssigneeName());
        view.setOwnerId(ticket.getOwnerId());
        view.setDeptId(ticket.getDeptId());
        view.setResponseDeadline(ticket.getResponseDeadline());
        view.setResolveDeadline(ticket.getResolveDeadline());
        view.setFirstRespondedAt(ticket.getFirstRespondedAt());
        view.setPausedSeconds(ticket.getPausedSeconds());
        view.setResolvedAt(ticket.getResolvedAt());
        view.setClosedAt(ticket.getClosedAt());
        view.setSolution(ticket.getSolution());
        view.setReopenCount(ticket.getReopenCount());
        view.setCreateTime(ticket.getCreateTime());
        view.setUpdateTime(ticket.getUpdateTime());
        view.setResponseOverdue(ticket.getFirstRespondedAt() == null && ticket.getResponseDeadline() != null
                && now.isAfter(ticket.getResponseDeadline()));
        view.setResolveOverdue(ticket.getResolvedAt() == null && ticket.getResolveDeadline() != null
                && now.isAfter(ticket.getResolveDeadline()));
        if (actions != null) {
            view.setActions(actions.stream().map(TicketActionView::from).toList());
        }
        return view;
    }
}
