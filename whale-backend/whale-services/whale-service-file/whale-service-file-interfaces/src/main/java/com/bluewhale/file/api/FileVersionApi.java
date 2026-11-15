package com.bluewhale.file.api;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.model.response.FileVersionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件版本 API 契约：新版本上传与版本列表。
 *
 * <p>权限不新增：列表复用 {@code sys:file:list}，上传复用 {@code sys:file:upload}。
 * 新版本为独立物理对象，按既有个人/租户/桶配额口径计量；秒传不参与版本。
 * 版本回滚与删除级联不在本批范围。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/file/v{version}/{id}/versions", version = "1")
public interface FileVersionApi {

    /**
     * 查询文件的版本组（包含根版本）。
     *
     * <p>传入任一版本行时回溯到版本组根文件；按版本号倒序返回，不含已逻辑删除的版本。</p>
     *
     * @param id 文件ID（根文件或任一版本行）
     * @param httpRequest 当前请求（用于判定调用者归属与文件管理权限）
     * @return 版本组列表
     */
    @GetMapping
    Result<List<FileVersionResponse>> list(@PathVariable Long id, HttpServletRequest httpRequest);

    /**
     * 上传文件新版本：写入新物理对象并登记版本行，版本号取组内最大值 + 1。
     *
     * @param id 文件ID（根文件或任一版本行）
     * @param file 新版本文件内容，必填
     * @param httpRequest 当前请求（用于读取调用者、权限码与生效上传上限）
     * @return 新版本元数据
     */
    @PostMapping
    Result<FileVersionResponse> upload(@PathVariable Long id,
                                       @RequestParam("file") MultipartFile file,
                                       HttpServletRequest httpRequest);

    /**
     * 回滚到指定历史版本：把目标版本内容复制为新版本（版本号取组内最大值 + 1）。
     *
     * <p>回滚不修改、不删除既有版本；复制产生独立物理对象并按目标版本字节数计入配额。
     * 不新增权限码，复用上传权限 {@code sys:file:upload}。</p>
     *
     * @param id 文件ID（根文件或任一版本行）
     * @param targetVersion 目标版本号
     * @param httpRequest 当前请求（用于读取调用者、权限码与生效上传上限）
     * @return 回滚生成的新版本元数据
     */
    @PostMapping("/{targetVersion}/rollback")
    Result<FileVersionResponse> rollback(@PathVariable Long id,
                                         @PathVariable int targetVersion,
                                         HttpServletRequest httpRequest);
}
