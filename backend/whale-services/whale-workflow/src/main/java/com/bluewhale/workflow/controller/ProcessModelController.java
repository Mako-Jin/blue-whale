package com.bluewhale.workflow.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.workflow.dto.BpmnImportRequest;
import com.bluewhale.workflow.dto.DefinitionSimulateRequest;
import com.bluewhale.workflow.dto.DefinitionValidateRequest;
import com.bluewhale.workflow.service.ProcessModelService;
import com.bluewhale.workflow.vo.ProcessDefinitionSimulateVO;
import com.bluewhale.workflow.vo.ProcessDefinitionValidateVO;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程模型接口：定义校验 / BPMN 导入 / BPMN 导出。
 *
 * <p>服务内路径前缀 {@code /workflow}；经网关按 {@code Path=/workflow/**} 规则转发，外部路径与本文档一致。
 * 校验与导入受 {@code @RequirePermission("sys:workflow:definition:*")} 保护，导出复用
 * {@code sys:workflow:list}（先例：whale-form 5.30 导出复用 {@code sys:form:data:list}）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class ProcessModelController {

    private final ProcessModelService processModelService;

    /**
     * 校验流程定义（JSON 或 BPMN XML），内容非法返回 {@code valid=false} 而非异常。
     *
     * @param request 校验请求：{@code definitionJson} 与 {@code bpmnXml} 二选一
     * @return 校验视图对象
     */
    @PostMapping("/definition/validate")
    @RequirePermission("sys:workflow:definition:validate")
    public Result<ProcessDefinitionValidateVO> validate(@RequestBody DefinitionValidateRequest request) {
        return Result.ok(processModelService.validate(request));
    }

    /**
     * 按真实条件求值模拟流程走向（复用校验权限点，不新增权限码）。
     *
     * @param request 模拟请求：{@code definitionJson} 与流程变量
     * @return 模拟结果视图对象
     */
    @PostMapping("/definition/simulate")
    @RequirePermission("sys:workflow:definition:validate")
    public Result<ProcessDefinitionSimulateVO> simulate(@Valid @RequestBody DefinitionSimulateRequest request) {
        return Result.ok(processModelService.simulate(request));
    }

    /**
     * 导入 BPMN XML 并建流程定义。
     *
     * @param request 导入请求：code / name / bpmnXml 必填
     * @return 流程定义视图对象
     */
    @PostMapping("/definition/bpmn")
    @RequirePermission("sys:workflow:definition:bpmnimport")
    public Result<ProcessDefinitionVO> importBpmn(@Valid @RequestBody BpmnImportRequest request) {
        return Result.ok(processModelService.importBpmn(request));
    }

    /**
     * 导出流程定义为 BPMN XML（二进制直出，不走 {@code Result} 信封）。
     *
     * @param id 流程定义ID
     * @return BPMN XML 字节响应
     */
    @GetMapping("/definition/{id}/bpmn")
    @RequirePermission("sys:workflow:list")
    public ResponseEntity<byte[]> exportBpmn(@PathVariable Long id) {
        byte[] content = processModelService.exportBpmn(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/xml;charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"process-" + id + ".bpmn20.xml\"")
                .body(content);
    }
}
