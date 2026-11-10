package com.bluewhale.doc.application.service;

import com.bluewhale.doc.interfaces.vo.DocExportVO;
import com.bluewhale.doc.interfaces.vo.DocImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档导入导出应用服务（M3）。
 *
 * @author BlueWhale
 */
public interface DocTransferService {

    /**
     * 导出页面正文。
     *
     * @param pageId 页面ID
     * @param format 格式：md / html，缺省 md
     * @param userId 当前用户ID
     * @return 导出内容
     */
    DocExportVO export(Long pageId, String format, Long userId);

    /**
     * 导入 Markdown / HTML 覆盖页面正文。
     *
     * @param pageId 页面ID
     * @param file   上传文件
     * @param format 格式：md / html，缺省按扩展名识别
     * @param userId 当前用户ID
     * @return 导入结果
     */
    DocImportResultVO importContent(Long pageId, MultipartFile file, String format, Long userId);
}
