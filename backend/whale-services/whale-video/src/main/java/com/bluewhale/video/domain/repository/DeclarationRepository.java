package com.bluewhale.video.domain.repository;

import com.bluewhale.video.domain.model.UploadDeclaration;

import java.util.Optional;

/**
 * 上传声明签署仓储端口。
 *
 * @author BlueWhale
 */
public interface DeclarationRepository {

    /**
     * 查询用户指定版本的签署记录。
     *
     * @param userId 用户 ID
     * @param version 声明版本
     * @return 签署记录；未签署返回空
     */
    Optional<UploadDeclaration> find(Long userId, String version);

    /**
     * 保存签署记录（同一用户同一版本只记录一次）。
     *
     * @param declaration 签署记录
     */
    void save(UploadDeclaration declaration);
}
