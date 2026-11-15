package com.bluewhale.file.api;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.model.request.FileBatchDeleteRequest;
import com.bluewhale.file.model.response.FileBatchDeleteResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/**
 * 文件批量操作 API 契约：批量逻辑删除与打包下载。
 *
 * <p>权限不新增：删除复用 {@code sys:file:delete}，打包复用 {@code sys:file:download}；
 * 单次文件数上限由配置 {@code whale.file.batch.max-files} 约束。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/file/v{version}/batch", version = "1")
public interface FileBatchApi {

    /**
     * 批量逻辑删除文件（逐项返回结果，单项失败不影响其余项）。
     *
     * @param request 待删除的文件ID列表
     * @param httpRequest 当前请求（用于读取调用者与权限码）
     * @return 逐项删除结果
     */
    @PostMapping("/delete")
    Result<FileBatchDeleteResponse> batchDelete(@Valid @RequestBody FileBatchDeleteRequest request,
                                                HttpServletRequest httpRequest);

    /**
     * 打包下载多个文件（ZIP 流式响应，不支持 Range 续传）。
     *
     * <p>{@code ids} 支持重复参数（{@code ?ids=1&ids=2}）与逗号分隔（{@code ?ids=1,2}）两种写法；
     * 全部文件通过可见性校验后才开始输出，因此参数与越权错误仍以常规 JSON 错误响应返回。</p>
     *
     * @param ids 待打包的文件ID列表
     * @param httpRequest 当前请求（用于读取调用者与权限码）
     * @return ZIP 流式响应
     */
    @GetMapping("/download")
    ResponseEntity<StreamingResponseBody> batchDownload(@RequestParam("ids") List<Long> ids,
                                                        HttpServletRequest httpRequest);
}
