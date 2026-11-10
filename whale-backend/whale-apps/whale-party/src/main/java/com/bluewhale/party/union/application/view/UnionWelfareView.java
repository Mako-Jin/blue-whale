package com.bluewhale.party.union.application.view;

import com.bluewhale.party.union.domain.model.UnionWelfare;
import com.bluewhale.party.union.domain.model.WelfareStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 节日福利批次视图。
 *
 * @author BlueWhale
 */
@Data
public class UnionWelfareView {

    /** 批次 ID */
    private Long id;

    /** 批次编号 */
    private String welfareNo;

    /** 福利名称 */
    private String title;

    /** 福利年度 */
    private Integer welfareYear;

    /** 发放标准 */
    private BigDecimal standardAmount;

    /** 状态码 */
    private String status;

    /** 发放时间 */
    private LocalDateTime issuedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 备注 */
    private String remark;

    /** 本次发放新增明细数（仅发放动作返回） */
    private Integer issueCount;

    /**
     * 由领域模型构建视图。
     *
     * @param welfare 福利批次
     * @return 视图
     */
    public static UnionWelfareView from(UnionWelfare welfare) {
        UnionWelfareView view = new UnionWelfareView();
        view.setId(welfare.getId());
        view.setWelfareNo(welfare.getWelfareNo());
        view.setTitle(welfare.getTitle());
        view.setWelfareYear(welfare.getWelfareYear());
        view.setStandardAmount(welfare.getStandardAmount());
        WelfareStatus status = welfare.getStatus() == null ? WelfareStatus.DRAFT : welfare.getStatus();
        view.setStatus(status.name());
        view.setIssuedAt(welfare.getIssuedAt());
        view.setClosedAt(welfare.getClosedAt());
        view.setRemark(welfare.getRemark());
        return view;
    }
}
