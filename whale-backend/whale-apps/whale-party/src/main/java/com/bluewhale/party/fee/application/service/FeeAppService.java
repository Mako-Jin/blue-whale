package com.bluewhale.party.fee.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.fee.application.command.FeePayCommand;
import com.bluewhale.party.fee.application.command.FeeRuleSaveCommand;
import com.bluewhale.party.fee.application.view.FeeBillNoticeView;
import com.bluewhale.party.fee.application.view.FeeBillView;
import com.bluewhale.party.fee.application.view.FeeGenerateResultView;
import com.bluewhale.party.fee.application.view.FeeMetricView;
import com.bluewhale.party.fee.application.view.FeeRuleView;
import com.bluewhale.party.fee.domain.model.FeeBillQuery;

import java.util.List;

/**
 * 党费台账应用服务（M-P1）。
 *
 * @author BlueWhale
 */
public interface FeeAppService {

    /**
     * 党费标准列表。
     *
     * @param actor 操作人
     * @return 档位列表
     */
    List<FeeRuleView> rules(PartyActor actor);

    /**
     * 新建 / 编辑党费档位（保存前校验区间不重叠）。
     *
     * @param id 档位 ID（为空表示新建）
     * @param command 保存命令
     * @param actor 操作人
     * @return 档位视图
     */
    FeeRuleView saveRule(Long id, FeeRuleSaveCommand command, PartyActor actor);

    /**
     * 初始化标准档位（仅在未配置任何档位时生效，幂等）。
     *
     * @param actor 操作人
     * @return 档位列表
     */
    List<FeeRuleView> initDefaultRules(PartyActor actor);

    /**
     * 账单分页。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<FeeBillView> bills(FeeBillQuery query, PartyActor actor);

    /**
     * 按月份生成账单（已生成的跳过，不覆盖）。
     *
     * @param periodMonth 所属月份 yyyyMM
     * @param branchId 支部 ID（为空表示全租户）
     * @param actor 操作人
     * @return 生成结果
     */
    FeeGenerateResultView generate(String periodMonth, Long branchId, PartyActor actor);

    /**
     * 登记缴费。
     *
     * @param billId 账单 ID
     * @param command 缴费命令
     * @param actor 操作人
     * @return 账单视图
     */
    FeeBillView pay(Long billId, FeePayCommand command, PartyActor actor);

    /**
     * 标记免缴。
     *
     * @param billId 账单 ID
     * @param reason 免缴原因
     * @param actor 操作人
     * @return 账单视图
     */
    FeeBillView waive(Long billId, String reason, PartyActor actor);

    /**
     * 党费收缴统计（M-P4，按所属月份 yyyyMM）。
     *
     * @param periodMonth 所属月份 yyyyMM
     * @param branchId 支部 ID（空表示全租户）
     * @return 收缴统计
     */
    FeeMetricView metrics(String periodMonth, Long branchId);

    /**
     * 欠缴账单清单（催缴通知扫描用）。
     *
     * @param periodMonth 所属月份 yyyyMM
     * @return 欠缴账单
     */
    List<FeeBillNoticeView> outstandingBills(String periodMonth);
}
