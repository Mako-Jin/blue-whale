package com.bluewhale.pdm.part.service;

import com.bluewhale.pdm.part.dto.PdmPartMergeRequest;
import com.bluewhale.pdm.part.vo.PdmPartDedupGroupVO;
import com.bluewhale.pdm.part.vo.PdmPartMergeResultVO;

import java.util.List;

/**
 * 零件去重候选与人工合并服务
 *
 * @author BlueWhale
 */
public interface PdmPartDedupService {

    /**
     * 查询去重候选组（归一化名称 + 材质 + 规格）
     *
     * @return 候选组，按候选数倒序
     */
    List<PdmPartDedupGroupVO> listCandidates();

    /**
     * 人工合并：改写草稿 BOM 引用并停用源零件
     *
     * @param request 合并请求
     * @return 合并结果
     */
    PdmPartMergeResultVO merge(PdmPartMergeRequest request);
}
