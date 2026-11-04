package com.bluewhale.pdm.part.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.part.dto.PdmBomDeriveRequest;
import com.bluewhale.pdm.part.dto.PdmBomLineSaveRequest;
import com.bluewhale.pdm.part.dto.PdmBomQueryRequest;
import com.bluewhale.pdm.part.dto.PdmBomSaveRequest;
import com.bluewhale.pdm.part.vo.PdmBomDiffVO;
import com.bluewhale.pdm.part.vo.PdmBomExportRowVO;
import com.bluewhale.pdm.part.vo.PdmBomReleaseVO;
import com.bluewhale.pdm.part.vo.PdmBomSnapshotVO;
import com.bluewhale.pdm.part.vo.PdmBomVO;
import com.bluewhale.pdm.part.vo.PdmPartWhereUsedVO;

import java.util.List;

/**
 * BOM 服务
 *
 * @author BlueWhale
 */
public interface PdmBomService {

    /**
     * 分页查询 BOM
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PdmBomVO> page(PdmBomQueryRequest request);

    /**
     * BOM 详情（头 + 行 + 树）
     *
     * @param id BOM ID
     * @return BOM 视图
     */
    PdmBomVO detail(Long id);

    /**
     * 新建 BOM 版本
     *
     * @param request 新建请求
     * @return BOM 视图
     */
    PdmBomVO create(PdmBomSaveRequest request);

    /**
     * 删除草稿 BOM
     *
     * @param id BOM ID
     */
    void delete(Long id);

    /**
     * 新增 BOM 行
     *
     * @param bomId   BOM ID
     * @param request 行请求
     * @return BOM 视图
     */
    PdmBomVO addLine(Long bomId, PdmBomLineSaveRequest request);

    /**
     * 更新 BOM 行
     *
     * @param bomId   BOM ID
     * @param lineId  行ID
     * @param request 行请求
     * @return BOM 视图
     */
    PdmBomVO updateLine(Long bomId, Long lineId, PdmBomLineSaveRequest request);

    /**
     * 删除 BOM 行
     *
     * @param bomId  BOM ID
     * @param lineId 行ID
     * @return BOM 视图
     */
    PdmBomVO deleteLine(Long bomId, Long lineId);

    /**
     * 发布 BOM（幂等）
     *
     * @param bomId  BOM ID
     * @param userId 发布人
     * @return 发布记录
     */
    PdmBomReleaseVO release(Long bomId, Long userId);

    /**
     * 查询发布记录
     *
     * @param bomNo BOM 编号
     * @return 发布记录列表
     */
    List<PdmBomReleaseVO> releases(String bomNo);

    /**
     * 读取发布快照
     *
     * @param bomNo     BOM 编号
     * @param versionNo 版本号
     * @return 快照
     */
    PdmBomSnapshotVO snapshot(String bomNo, String versionNo);

    /**
     * 查询零件使用处
     *
     * @param partId 零件ID
     * @return 使用处列表
     */
    List<PdmPartWhereUsedVO> whereUsed(Long partId);

    /**
     * 从 EBOM 派生 PBOM/MBOM（幂等：已有草稿直接返回）
     *
     * @param sourceId 源 BOM ID
     * @param request  派生请求
     * @return 派生 BOM 视图
     */
    PdmBomVO derive(Long sourceId, PdmBomDeriveRequest request);

    /**
     * 对比两个 BOM（同一产品）
     *
     * @param baseId   基线 BOM ID
     * @param targetId 目标 BOM ID
     * @return 对比结果
     */
    PdmBomDiffVO diff(Long baseId, Long targetId);

    /**
     * 导出 BOM 行（深度优先，含材质/规格）
     *
     * @param id BOM ID
     * @return 导出行
     */
    List<PdmBomExportRowVO> exportRows(Long id);
}
