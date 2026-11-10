package com.bluewhale.crm.marketing.application.view;

import com.bluewhale.crm.marketing.domain.model.Partner;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道伙伴视图。
 *
 * @author BlueWhale
 */
@Data
public class PartnerView {

    /** 伙伴 ID */
    private Long id;

    /** 伙伴编码 */
    private String partnerCode;

    /** 伙伴名称 */
    private String partnerName;

    /** 伙伴类型 */
    private String partnerType;

    /** 伙伴等级 */
    private String level;

    /** 所属区域 */
    private String region;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人电话 */
    private String contactPhone;

    /** 状态 */
    private String status;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 由聚合构建视图。
     *
     * @param partner 伙伴聚合
     * @return 视图
     */
    public static PartnerView from(Partner partner) {
        PartnerView view = new PartnerView();
        view.setId(partner.getId());
        view.setPartnerCode(partner.getPartnerCode());
        view.setPartnerName(partner.getPartnerName());
        view.setPartnerType(partner.getPartnerType() == null ? null : partner.getPartnerType().name());
        view.setLevel(partner.getLevel());
        view.setRegion(partner.getRegion());
        view.setContactName(partner.getContactName());
        view.setContactPhone(partner.getContactPhone());
        view.setStatus(partner.getStatus() == null ? null : partner.getStatus().name());
        view.setOwnerId(partner.getOwnerId());
        view.setDeptId(partner.getDeptId());
        view.setCreateTime(partner.getCreateTime());
        view.setUpdateTime(partner.getUpdateTime());
        return view;
    }
}
