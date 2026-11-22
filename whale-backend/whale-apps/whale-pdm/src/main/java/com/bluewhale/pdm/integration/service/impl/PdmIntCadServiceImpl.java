package com.bluewhale.pdm.integration.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.document.service.PdmDocDocumentService;
import com.bluewhale.pdm.document.support.PdmDocUploadFile;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import com.bluewhale.pdm.document.vo.PdmDocDocumentVO;
import com.bluewhale.pdm.document.vo.PdmDocVersionVO;
import com.bluewhale.pdm.dto.PdmProductQueryRequest;
import com.bluewhale.pdm.integration.dto.PdmIntCadEbomImportRequest;
import com.bluewhale.pdm.integration.service.PdmIntCadService;
import com.bluewhale.pdm.integration.service.PdmIntSyncService;
import com.bluewhale.pdm.integration.support.PdmIntAction;
import com.bluewhale.pdm.integration.support.PdmIntChannel;
import com.bluewhale.pdm.integration.support.PdmIntDirection;
import com.bluewhale.pdm.integration.support.PdmIntSyncStatus;
import com.bluewhale.pdm.integration.vo.PdmIntSyncLogVO;
import com.bluewhale.pdm.part.dto.PdmBomLineSaveRequest;
import com.bluewhale.pdm.part.dto.PdmBomSaveRequest;
import com.bluewhale.pdm.part.service.PdmBomService;
import com.bluewhale.pdm.part.vo.PdmBomVO;
import com.bluewhale.pdm.service.PdmProductService;
import com.bluewhale.pdm.vo.PdmProductVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * CAD 集成服务实现
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmIntCadServiceImpl implements PdmIntCadService {

    /** 单页检索上限 */
    private static final long PART_QUERY_SIZE = 20L;

    /** 默认 BOM 视图 */
    private static final String DEFAULT_VIEW = "EBOM";

    private final PdmDocDocumentService documentService;

    private final PdmBomService bomService;

    private final PdmProductService productService;

    private final PdmIntSyncService syncService;

    private final ObjectMapper objectMapper;

    @Override
    public PdmDocVersionVO checkin(Long documentId, String changeNote, String externalKey, PdmDocUploadFile upload,
                                   PdmDocViewer viewer, Long operatorId) {
        String key = "CAD:CHECKIN:" + externalKey;
        PdmDocVersionVO replay = replayCheckin(documentId, key, viewer);
        if (replay != null) {
            return replay;
        }
        PdmDocDocumentVO document = documentService.detail(documentId, viewer);
        try {
            PdmDocVersionVO version = documentService.checkIn(documentId, upload, changeNote, viewer);
            syncService.record(PdmIntChannel.CAD, PdmIntAction.CAD_CHECKIN, PdmIntDirection.INBOUND,
                    "DOCUMENT", documentId, document.getDocNo(), version.getVersionNo(), key,
                    changeNote, null, String.valueOf(version.getId()), null, operatorId);
            return version;
        } catch (BizException ex) {
            syncService.record(PdmIntChannel.CAD, PdmIntAction.CAD_CHECKIN, PdmIntDirection.INBOUND,
                    "DOCUMENT", documentId, document.getDocNo(), null, key,
                    changeNote, null, null, ex.getMessage(), operatorId);
            throw ex;
        }
    }

    @Override
    public PdmBomVO importEbom(PdmIntCadEbomImportRequest request, Long operatorId) {
        String key = "CAD:EBOM:" + request.getExternalKey();
        PdmIntSyncLogVO existing = syncService.findByKey(PdmIntChannel.CAD, key);
        if (existing != null && PdmIntSyncStatus.SUCCESS.equals(existing.getStatus())
                && StringUtils.hasText(existing.getExternalRef())) {
            return bomService.detail(Long.valueOf(existing.getExternalRef()));
        }
        PdmProductVO product = requirePart(request.getPartNo());
        PdmBomVO bom = null;
        try {
            PdmBomSaveRequest saveRequest = new PdmBomSaveRequest();
            saveRequest.setProductId(product.getId());
            saveRequest.setBomNo(StringUtils.hasText(request.getBomNo())
                    ? request.getBomNo() : product.getCode() + "-CAD");
            saveRequest.setView(StringUtils.hasText(request.getView()) ? request.getView() : DEFAULT_VIEW);
            saveRequest.setRemark(request.getRemark());
            bom = bomService.create(saveRequest);
            int lineNo = 1;
            for (PdmIntCadEbomImportRequest.Line line : request.getLines()) {
                PdmProductVO child = requirePart(line.getChildPartNo());
                PdmBomLineSaveRequest lineRequest = new PdmBomLineSaveRequest();
                lineRequest.setParentPartId(product.getId());
                lineRequest.setChildPartId(child.getId());
                lineRequest.setLineNo(lineNo++);
                lineRequest.setQty(line.getQty());
                lineRequest.setUom(StringUtils.hasText(line.getUom()) ? line.getUom() : child.getUom());
                lineRequest.setLossRate(line.getLossRate());
                lineRequest.setRemark(line.getRemark());
                bomService.addLine(bom.getId(), lineRequest);
            }
            PdmBomVO detail = bomService.detail(bom.getId());
            syncService.record(PdmIntChannel.CAD, PdmIntAction.CAD_EBOM_IMPORT, PdmIntDirection.INBOUND,
                    "BOM", detail.getId(), detail.getBomNo(), detail.getVersionNo(), key,
                    toJson(request), null, String.valueOf(detail.getId()), null, operatorId);
            return detail;
        } catch (BizException ex) {
            if (bom != null) {
                bomService.delete(bom.getId());
            }
            syncService.record(PdmIntChannel.CAD, PdmIntAction.CAD_EBOM_IMPORT, PdmIntDirection.INBOUND,
                    "BOM", bom == null ? null : bom.getId(), bom == null ? null : bom.getBomNo(), null, key,
                    toJson(request), null, null, ex.getMessage(), operatorId);
            throw ex;
        }
    }

    /**
     * 幂等重放：已成功检入直接返回原版本。
     *
     * @param documentId 文档ID
     * @param key        幂等键
     * @param viewer     访问者
     * @return 原版本，无需重放返回 {@code null}
     */
    private PdmDocVersionVO replayCheckin(Long documentId, String key, PdmDocViewer viewer) {
        PdmIntSyncLogVO existing = syncService.findByKey(PdmIntChannel.CAD, key);
        if (existing == null || !PdmIntSyncStatus.SUCCESS.equals(existing.getStatus())
                || !StringUtils.hasText(existing.getExternalRef())) {
            return null;
        }
        Long versionId = Long.valueOf(existing.getExternalRef());
        List<PdmDocVersionVO> versions = documentService.versions(documentId, viewer);
        return versions.stream().filter(version -> versionId.equals(version.getId())).findFirst().orElse(null);
    }

    /**
     * 按编码查询零件（精确匹配）。
     *
     * @param code 零件编码
     * @return 零件视图
     */
    private PdmProductVO requirePart(String code) {
        PdmProductQueryRequest query = new PdmProductQueryRequest();
        query.setCode(code);
        query.setSize(PART_QUERY_SIZE);
        PageResult<PdmProductVO> page = productService.page(query);
        return page.getRecords().stream()
                .filter(record -> code.equals(record.getCode()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "biz.pdm.integration.part-found", "零件不存在: {0}", code));
    }

    /**
     * 对象序列化为 JSON。
     *
     * @param value 对象
     * @return JSON 字符串
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("CAD 导入内容序列化失败: {}", ex.getMessage());
            return null;
        }
    }
}
