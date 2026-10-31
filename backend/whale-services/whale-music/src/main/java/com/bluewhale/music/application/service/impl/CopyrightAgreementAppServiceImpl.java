package com.bluewhale.music.application.service.impl;

import com.bluewhale.music.application.command.AgreementAcceptCommand;
import com.bluewhale.music.application.service.CopyrightAgreementAppService;
import com.bluewhale.music.application.view.AgreementView;
import com.bluewhale.music.config.MusicProperties;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.CopyrightAgreement;
import com.bluewhale.music.domain.repository.CopyrightAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 版权无争议协议应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CopyrightAgreementAppServiceImpl implements CopyrightAgreementAppService {

    /** 客户端 IP 字段长度上限 */
    private static final int MAX_CLIENT_IP_LENGTH = 64;

    /** 客户端标识字段长度上限 */
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final CopyrightAgreementRepository agreementRepository;

    private final MusicProperties musicProperties;

    @Override
    public AgreementView current(Long userId) {
        String version = musicProperties.getAgreementVersion();
        Optional<CopyrightAgreement> record = agreementRepository.findByUserAndVersion(userId, version);
        return toView(version, record.orElse(null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgreementView accept(AgreementAcceptCommand command, Long userId, String clientIp, String userAgent) {
        String version = musicProperties.getAgreementVersion();
        if (!version.equals(command.getAgreementVersion().trim())) {
            throw MusicErrorCode.exception(MusicErrorCode.AGREEMENT_VERSION_MISMATCH);
        }
        Optional<CopyrightAgreement> existing = agreementRepository.findByUserAndVersion(userId, version);
        if (existing.isPresent()) {
            return toView(version, existing.get());
        }
        CopyrightAgreement record = new CopyrightAgreement();
        record.setUserId(userId);
        record.setAgreementVersion(version);
        record.setAcceptedAt(LocalDateTime.now());
        record.setClientIp(truncate(clientIp, MAX_CLIENT_IP_LENGTH));
        record.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
        agreementRepository.insert(record);
        return toView(version, record);
    }

    @Override
    public void assertAccepted(Long userId) {
        String version = musicProperties.getAgreementVersion();
        agreementRepository.findByUserAndVersion(userId, version)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.AGREEMENT_REQUIRED));
    }

    /**
     * 组装协议状态视图。
     *
     * @param version 当前协议版本
     * @param record 签署记录，未签署为 {@code null}
     * @return 协议状态视图
     */
    private AgreementView toView(String version, CopyrightAgreement record) {
        AgreementView view = new AgreementView();
        view.setAgreementVersion(version);
        view.setAgreementTitle(musicProperties.getAgreementTitle());
        view.setAccepted(record != null);
        view.setAcceptedAt(record == null ? null : record.getAcceptedAt());
        return view;
    }

    /**
     * 截断超长文本，避免写入超出列长度的客户端信息。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本；原文为空返回 {@code null}
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
