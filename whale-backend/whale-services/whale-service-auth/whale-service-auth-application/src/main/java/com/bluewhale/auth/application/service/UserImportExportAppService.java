package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.importexport.ExportResult;
import com.bluewhale.auth.application.model.importexport.ImportCommitResult;
import com.bluewhale.auth.application.model.importexport.ImportPreviewResult;

import java.io.InputStream;

/**
 * 用户导入导出应用服务（权限中心扩展 M4）。
 *
 * <p>导入采用「预检 → 暂存 → 提交」两段式：预检全量校验并返回行级错误（不落库）；
 * 提交按每 500 行独立事务分批写入，密码列不导入，统一生成随机临时口令并强制首登改密，
 * 注册来源登记为 IMPORT；同名用户跳过计入「已存在」（幂等）。</p>
 *
 * @author BlueWhale
 */
public interface UserImportExportAppService {

    /**
     * 解析用户导入模板
     *
     * @return 模板 xlsx 字节内容
     */
    byte[] template();

    /**
     * 导入预检：解析上传文件并全量校验，全部通过时暂存批次返回 batchId
     *
     * @param input 上传文件输入流
     * @return 预检结果（存在错误时 batchId 为 {@code null}，错误定位到行/列）
     */
    ImportPreviewResult preview(InputStream input);

    /**
     * 导入提交：按预检暂存行分批写入（每批 ≤500，独立事务）
     *
     * @param batchId 预检返回的批次 ID（一次性消费，提交后失效）
     * @return 提交结果（成功/跳过/失败行数与统一临时口令）
     */
    ImportCommitResult commit(String batchId);

    /**
     * 导出用户：≤5 万行同步返回 xlsx 字节流，超过上传 whale-file 返回文件引用
     *
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @return 导出结果
     */
    ExportResult export(String keyword);
}
