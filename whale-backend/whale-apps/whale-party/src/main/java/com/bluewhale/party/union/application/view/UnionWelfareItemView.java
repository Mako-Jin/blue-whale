package com.bluewhale.party.union.application.view;

import com.bluewhale.party.union.domain.model.UnionWelfareItem;
import com.bluewhale.party.union.domain.model.WelfareSignStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 节日福利发放明细视图。
 *
 * <p>{@code welfareTitle} 只在我的福利列表回填：管理端明细页已经知道批次标题，
 * 每条都带上会让同一页出现重复文案。</p>
 *
 * @author BlueWhale
 */
@Data
public class UnionWelfareItemView {

    /** 明细 ID */
    private Long id;

    /** 福利批次 ID */
    private Long welfareId;

    /** 工会会员 ID */
    private Long unionMemberId;

    /** 员工编号 */
    private String employeeNo;

    /** 会员姓名 */
    private String memberName;

    /** 发放金额 */
    private BigDecimal amount;

    /** 签收状态码 */
    private String signStatus;

    /** 签收时间 */
    private LocalDateTime signedAt;

    /** 福利批次标题（我的福利回填） */
    private String welfareTitle;

    /**
     * 由领域模型构建视图。
     *
     * @param item 发放明细
     * @return 视图
     */
    public static UnionWelfareItemView from(UnionWelfareItem item) {
        return from(item, null);
    }

    /**
     * 由领域模型构建视图并回填批次标题。
     *
     * @param item 发放明细
     * @param welfareTitle 批次标题
     * @return 视图
     */
    public static UnionWelfareItemView from(UnionWelfareItem item, String welfareTitle) {
        UnionWelfareItemView view = new UnionWelfareItemView();
        view.setId(item.getId());
        view.setWelfareId(item.getWelfareId());
        view.setUnionMemberId(item.getUnionMemberId());
        view.setEmployeeNo(item.getEmployeeNo());
        view.setMemberName(item.getMemberName());
        view.setAmount(item.getAmount());
        WelfareSignStatus signStatus = item.getSignStatus() == null
                ? WelfareSignStatus.UNSIGNED : item.getSignStatus();
        view.setSignStatus(signStatus.name());
        view.setSignedAt(item.getSignedAt());
        view.setWelfareTitle(welfareTitle);
        return view;
    }
}
