package com.bluewhale.music.domain.repository;

import com.bluewhale.music.domain.model.CopyrightAgreement;

import java.util.Optional;

/**
 * 版权无争议协议签署仓储端口。
 *
 * @author BlueWhale
 */
public interface CopyrightAgreementRepository {

    /**
     * 查询某用户对指定协议版本的签署记录。
     *
     * @param userId 用户 ID
     * @param agreementVersion 协议版本号
     * @return 签署记录；未签署返回空
     */
    Optional<CopyrightAgreement> findByUserAndVersion(Long userId, String agreementVersion);

    /**
     * 新增签署记录（同一用户同一版本只允许一条）。
     *
     * @param agreement 签署记录
     */
    void insert(CopyrightAgreement agreement);
}
