package com.bluewhale.file.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.file.dto.FileQueryRequest;
import com.bluewhale.file.vo.FileInfoVO;
import com.bluewhale.file.vo.FileUploadVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件信息服务
 *
 * @author BlueWhale
 */
public interface FileInfoService {

    /**
     * 上传文件：写入存储并登记元数据
     *
     * @param file 上传的文件（必填）
     * @param bucket 目标桶名称；为空时取配置的默认桶
     * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
     * @return 上传结果视图对象
     */
    FileUploadVO upload(MultipartFile file, String bucket, Long uploaderId);

    /**
     * 分页查询文件元数据
     *
     * @param request 查询条件
     * @return 文件分页结果
     */
    PageResult<FileInfoVO> page(FileQueryRequest request);

    /**
     * 文件元数据详情
     *
     * @param id 文件ID
     * @return 文件视图对象
     */
    FileInfoVO getDetail(Long id);

    /**
     * 读取文件内容为流式资源
     *
     * @param id 文件ID
     * @return 文件内容资源
     */
    Resource loadResource(Long id);

    /**
     * 删除文件元数据（逻辑删除，物理对象保留）
     *
     * @param id 文件ID
     */
    void delete(Long id);
}
