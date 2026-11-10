package com.bluewhale.doc.application.service.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.support.DocMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 页面正文乐观锁写入支撑。
 *
 * <p>保存使用 {@code UPDATE ... WHERE id = ? AND revision = ?} 条件更新；更新失败时按
 * 页面是否仍存在区分「版本冲突」与「页面不存在」，避免覆盖他人修改。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class DocPageMutationSupport {

    /** 保存后 revision 的递增量 */
    private static final long REVISION_STEP = 1L;

    private final DocPageMapper pageMapper;

    private final DocMetrics docMetrics;

    /**
     * 按客户端持有的 revision 保存页面正文与可选标题。
     *
     * @param page        当前页面
     * @param baseRevision 客户端持有的 revision
     * @param title       新标题；null 表示不修改
     * @param contentJson 新正文 JSON；null 表示不修改
     * @param contentText 服务端提取的纯文本投影
     * @param editorId    编辑人ID
     * @return 更新后的页面
     * @throws com.bluewhale.common.core.exception.BizException 版本冲突或页面不存在
     */
    public DocPage apply(DocPage page, Long baseRevision, String title, String contentJson,
                         String contentText, Long editorId) {
        if (!Objects.equals(page.getRevision(), baseRevision)) {
            docMetrics.recordSaveConflict();
            throw DocErrorCode.exception(DocErrorCode.REVISION_CONFLICT);
        }
        long nextRevision = baseRevision + REVISION_STEP;
        LambdaUpdateWrapper<DocPage> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(DocPage::getId, page.getId())
                .eq(DocPage::getRevision, baseRevision)
                .set(DocPage::getRevision, nextRevision)
                .set(DocPage::getLastEditorId, editorId)
                .set(DocPage::getUpdateTime, LocalDateTime.now());
        if (title != null) {
            wrapper.set(DocPage::getTitle, title);
        }
        if (contentJson != null) {
            wrapper.set(DocPage::getContentJson, contentJson)
                    .set(DocPage::getContentText, contentText);
        }
        int rows = pageMapper.update(null, wrapper);
        if (rows == 0) {
            throw resolveWriteFailure(page.getId());
        }
        return pageMapper.selectById(page.getId());
    }

    /**
     * 区分条件更新失败的原因。
     *
     * @param pageId 页面ID
     * @return 页面不存在时返回 40401 业务异常，否则返回 40901 冲突异常
     */
    private RuntimeException resolveWriteFailure(Long pageId) {
        if (pageMapper.selectById(pageId) == null) {
            return DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        docMetrics.recordSaveConflict();
        return DocErrorCode.exception(DocErrorCode.REVISION_CONFLICT);
    }
}
