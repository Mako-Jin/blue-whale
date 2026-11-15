package com.bluewhale.file.service;

import com.bluewhale.file.model.result.FileArchiveEntryResult;

import java.io.OutputStream;
import java.util.List;

/**
 * 文件打包服务：把一批文件流式写成 ZIP。
 *
 * <p>两阶段执行：{@link #prepare} 完成可见性、数量与体积校验并生成去重条目名，
 * 错误在响应提交前以业务异常返回；{@link #writeZip} 仅做流式复制，不创建本地临时文件，
 * 内存占用与文件大小无关。</p>
 *
 * @author BlueWhale
 */
public interface FileArchiveService {

    /**
     * 校验待打包文件并生成 ZIP 条目
     *
     * <p>ID 去重后保持请求顺序；文件数超过 {@code whale.file.batch.max-files}、
     * 未压缩总字节数超过 {@code whale.file.batch.max-zip-bytes} 或存在不可见文件时整体拒绝。</p>
     *
     * @param ids 待打包的文件ID列表，可为 {@code null}
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @return 校验通过的条目（按请求顺序，条目名已去重）
     * @throws com.bluewhale.common.core.exception.BizException 参数非法或文件不可见时抛出
     */
    List<FileArchiveEntryResult> prepare(List<Long> ids, Long requesterId, boolean canManage);

    /**
     * 把已校验的条目流式写入 ZIP
     *
     * <p>响应提交后执行，读取失败无法再改变 HTTP 状态；调用方负责关闭 {@code out}。</p>
     *
     * @param entries 已校验的条目
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @param out 响应输出流
     */
    void writeZip(List<FileArchiveEntryResult> entries, Long requesterId, boolean canManage, OutputStream out);
}
