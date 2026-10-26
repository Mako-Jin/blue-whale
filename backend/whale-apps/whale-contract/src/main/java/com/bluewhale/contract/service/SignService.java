package com.bluewhale.contract.service;

import com.bluewhale.contract.dto.OperatorContext;
import com.bluewhale.contract.dto.SignExecuteRequest;
import com.bluewhale.contract.dto.SignRefuseRequest;
import com.bluewhale.contract.dto.SignStartRequest;
import com.bluewhale.contract.vo.EvidenceVO;
import com.bluewhale.contract.vo.SignResultVO;
import com.bluewhale.contract.vo.SignTaskVO;
import com.bluewhale.contract.vo.VerifyResultVO;

import java.util.List;

/**
 * 合同签署服务。
 *
 * @author BlueWhale
 */
public interface SignService {

    /**
     * 发起签署：按相对方或指定任务生成签署任务，合同转入 SIGNING。
     *
     * @param contractId 合同ID
     * @param request    发起请求
     * @param operator   当前操作人
     * @return 签署任务列表
     */
    List<SignTaskVO> start(Long contractId, SignStartRequest request, OperatorContext operator);

    /**
     * 查询合同的签署任务。
     *
     * @param contractId 合同ID
     * @return 任务列表
     */
    List<SignTaskVO> tasks(Long contractId);

    /**
     * 查询当前用户的待签署任务。
     *
     * @param operator 当前操作人
     * @return 待签任务列表
     */
    List<SignTaskVO> pending(OperatorContext operator);

    /**
     * 本地签署（视觉盖章 + 可选数字签名 + 证据入库）。
     *
     * @param taskId   签署任务ID
     * @param request  签署参数
     * @param operator 当前操作人
     * @param clientIp 客户端IP
     * @return 签署结果
     */
    SignResultVO sign(Long taskId, SignExecuteRequest request, OperatorContext operator, String clientIp);

    /**
     * 拒签。
     *
     * @param taskId   签署任务ID
     * @param request  拒签请求
     * @param operator 当前操作人
     */
    void refuse(Long taskId, SignRefuseRequest request, OperatorContext operator);

    /**
     * 查询证据链。
     *
     * @param contractId 合同ID
     * @return 证据链视图
     */
    EvidenceVO evidence(Long contractId);

    /**
     * 验签。
     *
     * @param contractId 合同ID
     * @return 验签结果
     */
    VerifyResultVO verify(Long contractId);

    /**
     * 作废合同（保留签署证据）。
     *
     * @param contractId 合同ID
     */
    void voidContract(Long contractId);
}
