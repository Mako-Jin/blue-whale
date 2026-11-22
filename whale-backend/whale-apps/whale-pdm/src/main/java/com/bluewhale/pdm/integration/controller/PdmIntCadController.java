package com.bluewhale.pdm.integration.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.document.support.PdmDocUploadFile;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import com.bluewhale.pdm.document.vo.PdmDocVersionVO;
import com.bluewhale.pdm.integration.dto.PdmIntCadEbomImportRequest;
import com.bluewhale.pdm.integration.service.PdmIntCadService;
import com.bluewhale.pdm.integration.support.PdmIntRequestContext;
import com.bluewhale.pdm.part.vo.PdmBomVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * CAD 集成契约接口
 *
 * <p>供外部 CAD 工具按契约检入图纸、导入装配 EBOM；以工具侧幂等键去重，
 * 重复调用不生成新版本或重复 BOM。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pdm/v{version}/integration/cad", version = "1")
@RequiredArgsConstructor
public class PdmIntCadController {

    /** 审计模块 */
    private static final String MODULE = "PDM 集成";

    private final PdmIntCadService cadService;

    /**
     * CAD 检入文档新版本
     *
     * @param documentId    文档ID
     * @param changeNote    变更说明
     * @param externalKey   工具侧幂等键
     * @param file          文件
     * @param servletRequest 当前请求
     * @return 版本视图
     */
    @PostMapping("/checkin")
    @RequirePermission("sys:pdm:int:cad:checkin")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "CAD 检入图纸",
            targetType = "pdm_doc_version")
    public Result<PdmDocVersionVO> checkin(@RequestParam Long documentId,
                                           @RequestParam(required = false) String changeNote,
                                           @RequestParam String externalKey,
                                           @RequestPart("file") MultipartFile file,
                                           HttpServletRequest servletRequest) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "检入文件不能为空");
        }
        return Result.ok(cadService.checkin(documentId, changeNote, externalKey, toUploadFile(file),
                PdmDocViewer.of(servletRequest), PdmIntRequestContext.requireUserId(servletRequest)));
    }

    /**
     * 导入 CAD 装配 EBOM 草稿
     *
     * @param request        导入请求
     * @param servletRequest 当前请求
     * @return BOM 视图
     */
    @PostMapping("/ebom-import")
    @RequirePermission("sys:pdm:int:cad:import")
    @AuditLog(module = MODULE, type = OperateType.CREATE, operation = "CAD 导入 EBOM",
            targetType = "pdm_bom")
    public Result<PdmBomVO> importEbom(@Valid @RequestBody PdmIntCadEbomImportRequest request,
                                       HttpServletRequest servletRequest) {
        return Result.ok(cadService.importEbom(request,
                PdmIntRequestContext.requireUserId(servletRequest)));
    }

    /**
     * 构造上传文件对象。
     *
     * @param file 上传文件
     * @return 上传文件
     */
    private PdmDocUploadFile toUploadFile(MultipartFile file) {
        try {
            return new PdmDocUploadFile(file.getBytes(), file.getOriginalFilename(),
                    file.getContentType(), file.getSize());
        } catch (IOException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "读取检入文件失败");
        }
    }
}
