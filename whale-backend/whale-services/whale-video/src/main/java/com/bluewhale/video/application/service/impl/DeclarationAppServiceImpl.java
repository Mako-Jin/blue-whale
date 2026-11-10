package com.bluewhale.video.application.service.impl;

import com.bluewhale.video.application.command.DeclarationAcceptCommand;
import com.bluewhale.video.application.service.DeclarationAppService;
import com.bluewhale.video.application.view.DeclarationView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.CopyrightSource;
import com.bluewhale.video.domain.model.UploadDeclaration;
import com.bluewhale.video.domain.repository.DeclarationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 上传声明应用服务实现。
 *
 * <p>声明版本必须与服务端当前版本一致；同一用户同一版本重复签署幂等，
 * 不覆盖首次签署时间，避免留痕被篡改。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class DeclarationAppServiceImpl implements DeclarationAppService {

    /** 声明正文（纯文本条款，展示与留痕用） */
    private static final String DECLARATION_TEXT = "本人承诺所上传内容合法合规，拥有完整版权或已取得合法授权，"
            + "不存在侵犯第三方著作权、肖像权、商标权等权利的情形；如因内容侵权产生纠纷，由本人承担全部责任。"
            + "平台有权对涉嫌侵权内容做下架处理并保留追溯权利。";

    private final DeclarationRepository declarationRepository;

    private final VideoProperties videoProperties;

    @Override
    public DeclarationView current(Long userId) {
        DeclarationView view = new DeclarationView();
        view.setVersion(videoProperties.getDeclarationVersion());
        view.setTitle(videoProperties.getDeclarationTitle());
        view.setText(DECLARATION_TEXT);
        declarationRepository.find(userId, videoProperties.getDeclarationVersion()).ifPresent(declaration -> {
            view.setAccepted(true);
            view.setAcceptedAt(declaration.getAcceptedAt());
            view.setContentSource(declaration.getContentSource() == null ? null
                    : declaration.getContentSource().name());
        });
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeclarationView accept(DeclarationAcceptCommand command, Long userId, String clientIp, String userAgent) {
        if (!videoProperties.getDeclarationVersion().equals(command.getVersion())) {
            throw VideoErrorCode.exception(VideoErrorCode.DECLARATION_VERSION_MISMATCH);
        }
        CopyrightSource source = CopyrightSource.parse(command.getContentSource());
        if (source == null) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_SOURCE_INVALID);
        }
        if (declarationRepository.find(userId, command.getVersion()).isEmpty()) {
            UploadDeclaration declaration = new UploadDeclaration();
            declaration.setUserId(userId);
            declaration.setDeclarationVersion(command.getVersion());
            declaration.setAcceptedAt(LocalDateTime.now());
            declaration.setClientIp(clientIp);
            declaration.setUserAgent(userAgent);
            declaration.setContentSource(source);
            declarationRepository.save(declaration);
        }
        return current(userId);
    }
}
