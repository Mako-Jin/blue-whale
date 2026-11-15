package com.bluewhale.form.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.model.request.FormDataApprovalCallbackRequest;
import com.bluewhale.form.model.request.FormDataApprovalStartRequest;
import com.bluewhale.form.model.request.FormDataQueryRequest;
import com.bluewhale.form.model.request.FormDataSubmitRequest;
import com.bluewhale.form.model.request.FormDataUpdateRequest;
import com.bluewhale.form.model.vo.FormArchiveVO;
import com.bluewhale.form.model.vo.FormDataExportVO;
import com.bluewhale.form.model.vo.FormDataHistoryVO;
import com.bluewhale.form.model.vo.FormDataImportResultVO;
import com.bluewhale.form.model.vo.FormDataVO;

import java.util.List;

/**
 * 表单数据服务
 *
 * @author BlueWhale
 */
public interface FormDataService {

    /**
     * 提交表单数据：校验表单定义存在且当前版本已发布，按 schema 校验字段后双写主表与物理表
     *
     * @param request 提交请求：definitionId / dataJson 必填，bizKey 可选
     * @param submitter 提交人账号，可为 {@code null}
     * @param tenantId 租户ID（行级隔离）
     * @return 表单数据视图对象（含结构化 {@code fields}）
     */
    FormDataVO submit(FormDataSubmitRequest request, String submitter, Long tenantId);

    /**
     * 修改表单数据（审批环节改表单）：合并提交字段后双写主表与物理表
     *
     * @param id 表单数据ID
     * @param request 修改请求：{@code dataJson} 必填
     * @param tenantId 租户ID（行级隔离）
     * @return 表单数据视图对象（含结构化 {@code fields}）
     */
    FormDataVO update(Long id, FormDataUpdateRequest request, Long tenantId);

    /**
     * 表单数据详情：主记录 + 按其版本从物理表读取的结构化字段
     *
     * @param id 表单数据ID
     * @param tenantId 租户ID
     * @return 表单数据视图对象（含结构化 {@code fields}）
     */
    FormDataVO getDetail(Long id, Long tenantId);

    /**
     * 表单归档详情：表单数据（含结构化字段）+ 字段定义（含附件/子表结构）
     *
     * <p>仅供档案服务（whale-archive）生成快照，网关对 {@code /form/internal/**} 返回 403，外部不可达。</p>
     *
     * @param id 表单数据ID
     * @param tenantId 租户ID
     * @return 归档详情视图对象
     */
    FormArchiveVO getArchiveDetail(Long id, Long tenantId);

    /**
     * 分页查询表单数据（按租户过滤）
     *
     * @param request 查询条件
     * @param tenantId 租户ID
     * @return 表单数据分页结果
     */
    PageResult<FormDataVO> page(FormDataQueryRequest request, Long tenantId);

    /**
     * 删除表单数据（主表与物理表逻辑删除）
     *
     * @param id 表单数据ID
     * @param tenantId 租户ID
     */
    void delete(Long id, Long tenantId);

    /**
     * 导出表单数据为 CSV / XLSX 字节流
     *
     * <p>列顺序 = 公共列（ID/表单编码/版本/业务键/提交人/提交时间/状态）+ 表单视图的数据字段
     * （取自 {@code schema_json} 快照的 {@code fields[]}，键为 {@code columnCode}）；行取主表
     * {@code form_data} 并解析 {@code dataJson} 还原各列值，单次查询无 N+1。</p>
     *
     * @param request 查询条件：{@code definitionCode} 必填，{@code bizKey} / {@code submitter} 可选
     * @param tenantId 租户ID
     * @param format 导出格式：{@code csv} 或 {@code xlsx}
     * @return 导出产物（内容 + 文件名 + Content-Type）
     */
    FormDataExportVO export(FormDataQueryRequest request, Long tenantId, String format);

    /**
     * 查询表单数据的变更历史（按变更时间倒序，最多 200 条）
     *
     * <p>记录删除后仍可追溯：不校验主记录是否存在，仅以「租户 + 数据ID」限定范围，
     * 跨租户查询返回空列表而非泄漏他人历史。</p>
     *
     * @param id 表单数据ID
     * @param tenantId 租户ID
     * @return 变更历史列表（无记录返回空列表）
     */
    List<FormDataHistoryVO> listHistory(Long id, Long tenantId);

    /**
     * 发起审批：登记外部工作流实例ID 并把业务状态置为「审批中」
     *
     * <p>审批状态机由外部工作流承载，本模块只做登记与只读判定。审批中的记录不可修改、不可删除。</p>
     *
     * @param id 表单数据ID
     * @param request 发起审批请求（流程实例ID）
     * @param tenantId 租户ID
     * @return 表单数据视图对象（业务状态已更新）
     */
    FormDataVO startApproval(Long id, FormDataApprovalStartRequest request, Long tenantId);

    /**
     * 处理审批结果回调（内网接口）：通过置 3、驳回置 4，非审批中状态幂等返回
     *
     * @param request 回调请求（流程实例ID + 结果）
     * @param tenantId 租户ID
     */
    void handleApprovalCallback(FormDataApprovalCallbackRequest request, Long tenantId);

    /**
     * 批量导入表单数据（CSV，首行表头匹配字段显示名或字段编码）
     *
     * @param definitionCode 表单编码
     * @param content CSV 文件内容
     * @param partial {@code true} 逐行独立事务（部分成功）；{@code false} 整批事务（任一失败全部回滚）
     * @param submitter 提交人账号
     * @param tenantId 租户ID
     * @return 导入结果（总数、成功数、失败行明细）
     */
    FormDataImportResultVO importData(String definitionCode, byte[] content, boolean partial,
                                      String submitter, Long tenantId);

    /**
     * 生成导入模板（CSV，表头为可导入字段的显示名）
     *
     * @param definitionCode 表单编码
     * @return 模板 CSV 字节流（UTF-8 + BOM）
     */
    byte[] importTemplate(String definitionCode);
}
