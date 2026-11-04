package com.bluewhale.oa.common.application.service.impl;

import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.command.FileRefRegisterCommand;
import com.bluewhale.oa.common.application.service.FileRefAppService;
import com.bluewhale.oa.common.application.view.FileRefView;
import com.bluewhale.oa.common.domain.model.FileRef;
import com.bluewhale.oa.common.domain.repository.FileRefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 文件引用应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FileRefAppServiceImpl implements FileRefAppService {

    private final FileRefRepository fileRefRepository;

    @Override
    public List<FileRefView> listByBiz(String bizType, Long bizId) {
        if (!StringUtils.hasText(bizType) || bizId == null) {
            throw OaErrorCode.exception(OaErrorCode.FILE_REF_INVALID);
        }
        return fileRefRepository.listByBiz(bizType, bizId).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileRefView register(FileRefRegisterCommand command) {
        if (!StringUtils.hasText(command.getBizType()) || command.getBizId() == null || command.getFileId() == null) {
            throw OaErrorCode.exception(OaErrorCode.FILE_REF_INVALID);
        }
        FileRef fileRef = new FileRef();
        fileRef.setBizType(command.getBizType());
        fileRef.setBizId(command.getBizId());
        fileRef.setFileId(command.getFileId());
        fileRef.setFileName(command.getFileName());
        fileRef.setFileSize(command.getFileSize());
        fileRef.setContentType(command.getContentType());
        fileRef.setUploadedBy(command.getUploadedBy());
        fileRef.setPurpose(command.getPurpose());
        FileRef saved = fileRefRepository.insertIfAbsent(fileRef)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.FILE_REF_EXISTS));
        return toView(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        fileRefRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.FILE_REF_NOT_FOUND));
        fileRefRepository.deleteById(id);
    }

    /**
     * 领域模型转视图。
     *
     * @param fileRef 文件引用
     * @return 文件引用视图
     */
    private FileRefView toView(FileRef fileRef) {
        FileRefView view = new FileRefView();
        view.setId(fileRef.getId());
        view.setBizType(fileRef.getBizType());
        view.setBizId(fileRef.getBizId());
        view.setFileName(fileRef.getFileName());
        view.setFileSize(fileRef.getFileSize());
        view.setContentType(fileRef.getContentType());
        view.setUploadedBy(fileRef.getUploadedBy());
        view.setPurpose(fileRef.getPurpose());
        view.setCreateTime(fileRef.getCreateTime());
        return view;
    }
}
