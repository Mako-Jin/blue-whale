package com.bluewhale.contract.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.contract.dto.ContractQueryRequest;
import com.bluewhale.contract.dto.ContractSaveRequest;
import com.bluewhale.contract.dto.ContractVersionSaveRequest;
import com.bluewhale.contract.dto.OperatorContext;
import com.bluewhale.contract.vo.ContractVO;
import com.bluewhale.contract.vo.ContractVersionVO;

import java.util.List;

/**
 * 合同主流程服务
 *
 * @author BlueWhale
 */
public interface ContractService {

    /**
     * 分页查询合同
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<ContractVO> page(ContractQueryRequest request);

    /**
     * 按弱关联业务查询合同投影（内网只读）。
     *
     * @param bizType 业务类型，不能为空
     * @param bizId 业务 ID，不能为空
     * @return 合同视图列表（不加载相对方 / 版本 / 流程）
     */
    List<ContractVO> listByBiz(String bizType, String bizId);

    /**
     * 合同详情（APPROVING 状态会主动同步一次审批结果）
     *
     * @param id 合同ID
     * @return 合同详情
     */
    ContractVO getDetail(Long id);

    /**
     * 新建合同（草稿 + 版本 1）
     *
     * @param request  新增请求
     * @param operator 当前操作人
     * @return 新增后的合同
     */
    ContractVO create(ContractSaveRequest request, OperatorContext operator);

    /**
     * 编辑合同（仅 DRAFT）
     *
     * @param id      合同ID
     * @param request 更新请求
     * @return 更新后的合同
     */
    ContractVO update(Long id, ContractSaveRequest request);

    /**
     * 逻辑删除合同（仅 DRAFT）
     *
     * @param id 合同ID
     */
    void delete(Long id);

    /**
     * 提交审批（启动工作流实例）
     *
     * @param id       合同ID
     * @param operator 当前操作人
     * @return 提交后的合同
     */
    ContractVO submit(Long id, OperatorContext operator);

    /**
     * 撤回审批（仅 APPROVING）
     *
     * @param id       合同ID
     * @param operator 当前操作人
     * @return 撤回后的合同
     */
    ContractVO withdraw(Long id, OperatorContext operator);

    /**
     * 终止合同（APPROVED/SIGNING/SIGNED/EFFECTIVE）
     *
     * @param id 合同ID
     * @return 终止后的合同
     */
    ContractVO terminate(Long id);

    /**
     * 合同生效（仅 SIGNED）
     *
     * @param id 合同ID
     * @return 生效后的合同
     */
    ContractVO effective(Long id);

    /**
     * 归档合同（仅 EFFECTIVE）
     *
     * @param id 合同ID
     * @return 归档后的合同
     */
    ContractVO archive(Long id);

    /**
     * 版本列表
     *
     * @param id 合同ID
     * @return 版本列表（版本号降序）
     */
    List<ContractVersionVO> listVersions(Long id);

    /**
     * 基于当前版本发起修订（版本号 +1，合同回到 DRAFT）
     *
     * @param id      合同ID
     * @param request 修订请求
     * @return 新版本
     */
    ContractVersionVO createVersion(Long id, ContractVersionSaveRequest request);
}
