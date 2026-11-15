package com.bluewhale.file.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.file.model.command.CreateShareCommand;
import com.bluewhale.file.model.query.FileSharePageQuery;
import com.bluewhale.file.model.result.FileShareAccessResult;
import com.bluewhale.file.model.result.FileShareCreateResult;
import com.bluewhale.file.model.result.FileShareResult;

import java.io.InputStream;

/**
 * 文件分享服务
 *
 * @author BlueWhale
 */
public interface FileShareService {

    /**
     * 创建分享链接
     *
     * @param command 创建命令（文件ID、有效期、次数上限、密码、调用者）
     * @return 创建结果（令牌与失效时间）
     */
    FileShareCreateResult create(CreateShareCommand command);

    /**
     * 分页查询分享链接：默认仅创建人本人，管理权限可查看全租户
     *
     * @param query 分页与可见范围
     * @return 分享分页结果
     */
    PageResult<FileShareResult> page(FileSharePageQuery query);

    /**
     * 撤销分享链接（幂等）
     *
     * @param id 分享ID
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     */
    void revoke(Long id, Long requesterId, boolean canManage);

    /**
     * 凭令牌访问分享元数据
     *
     * <p>需要密码且未提供时只返回 {@code needPassword=true}，不返回文件名与大小；
     * 密码错误抛 {@code FORBIDDEN}；过期、撤销、超次数统一按不存在处理。</p>
     *
     * @param token 分享令牌
     * @param password 明文分享密码，可为 {@code null}
     * @return 访问结果
     */
    FileShareAccessResult access(String token, String password);

    /**
     * 凭令牌读取文件内容（每次调用占用一次下载次数）
     *
     * @param token 分享令牌
     * @param password 明文分享密码，可为 {@code null}
     * @param offset 起始偏移；为 {@code null} 时读取全量
     * @param length 读取长度；为 {@code null} 时读取全量
     * @return 内容输入流，由调用方负责关闭
     */
    InputStream openContent(String token, String password, Long offset, Long length);
}
