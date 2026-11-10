package com.bluewhale.erp.qc.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.qc.application.command.InspectionCreateCommand;
import com.bluewhale.erp.qc.application.command.InspectionRecordCommand;
import com.bluewhale.erp.qc.application.command.NcrCloseCommand;
import com.bluewhale.erp.qc.application.command.NcrCreateCommand;
import com.bluewhale.erp.qc.application.command.NcrDisposeCommand;
import com.bluewhale.erp.qc.application.view.InspectionView;
import com.bluewhale.erp.qc.application.view.NcrView;
import com.bluewhale.erp.qc.application.view.QualityCostView;
import com.bluewhale.erp.qc.domain.model.QcQuery;

/**
 * 质量检验与不合格品应用服务。
 *
 * @author BlueWhale
 */
public interface QualityInspectionAppService {

    /**
     * 分页查询检验单。
     *
     * @param query 查询条件
     * @return 检验单分页
     */
    PageResult<InspectionView> pageInspections(QcQuery query);

    /**
     * 检验单详情。
     *
     * @param id 主键
     * @return 检验单
     */
    InspectionView detailInspection(Long id);

    /**
     * 新增检验单。
     *
     * @param command 创建命令
     * @return 检验单
     */
    InspectionView createInspection(InspectionCreateCommand command);

    /**
     * 录入检验项。
     *
     * @param id 检验单主键
     * @param command 录入命令
     * @return 检验单
     */
    InspectionView recordInspection(Long id, InspectionRecordCommand command);

    /**
     * 判定检验单；不合格时自动生成不合格品单。
     *
     * @param id 检验单主键
     * @return 检验单
     */
    InspectionView judgeInspection(Long id);

    /**
     * 分页查询不合格品单。
     *
     * @param query 查询条件
     * @return 不合格品单分页
     */
    PageResult<NcrView> pageNcr(QcQuery query);

    /**
     * 手工登记不合格品单。
     *
     * @param command 创建命令
     * @return 不合格品单
     */
    NcrView createNcr(NcrCreateCommand command);

    /**
     * 处置不合格品单并登记质量成本。
     *
     * @param id 主键
     * @param command 处置命令
     * @return 不合格品单
     */
    NcrView disposeNcr(Long id, NcrDisposeCommand command);

    /**
     * 关闭不合格品单。
     *
     * @param id 主键
     * @param command 关闭命令
     * @return 不合格品单
     */
    NcrView closeNcr(Long id, NcrCloseCommand command);

    /**
     * 分页查询质量成本。
     *
     * @param query 查询条件
     * @return 质量成本分页
     */
    PageResult<QualityCostView> pageQualityCost(QcQuery query);
}
