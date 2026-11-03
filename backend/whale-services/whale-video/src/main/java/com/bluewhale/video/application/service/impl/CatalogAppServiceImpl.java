package com.bluewhale.video.application.service.impl;

import com.bluewhale.video.application.service.CatalogAppService;
import com.bluewhale.video.application.view.CategoryView;
import com.bluewhale.video.application.view.CreatorView;
import com.bluewhale.video.application.view.TagView;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.Category;
import com.bluewhale.video.domain.model.Creator;
import com.bluewhale.video.domain.model.Tag;
import com.bluewhale.video.domain.repository.CategoryRepository;
import com.bluewhale.video.domain.repository.CreatorRepository;
import com.bluewhale.video.domain.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 目录应用服务实现（分类 / 标签 / 创作者资料）。
 *
 * <p>分类与标签仅返回启用数据；创作者头像通过 {@code /video/creator/{id}/avatar}
 * 代理访问，公开视图不暴露文件 ID。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CatalogAppServiceImpl implements CatalogAppService {

    /** 创作者头像代理地址前缀 */
    private static final String AVATAR_PATH_PREFIX = "/video/creator/";

    /** 创作者头像代理地址后缀 */
    private static final String AVATAR_PATH_SUFFIX = "/avatar";

    private final CategoryRepository categoryRepository;

    private final TagRepository tagRepository;

    private final CreatorRepository creatorRepository;

    @Override
    public List<CategoryView> categories() {
        return categoryRepository.listEnabled().stream().map(this::toView).toList();
    }

    @Override
    public List<TagView> tags() {
        return tagRepository.listAll().stream().map(this::toView).toList();
    }

    @Override
    public CreatorView creator(Long creatorId) {
        Creator creator = requireCreator(creatorId);
        CreatorView view = new CreatorView();
        view.setId(creator.getId());
        view.setUserId(creator.getUserId());
        view.setDisplayName(creator.getDisplayName());
        view.setBio(creator.getBio());
        view.setActive(creator.active());
        view.setAvatarUrl(creator.getAvatarFileId() == null || creator.getAvatarFileId() <= 0
                ? null : AVATAR_PATH_PREFIX + creator.getId() + AVATAR_PATH_SUFFIX);
        return view;
    }

    @Override
    public Long avatarFileId(Long creatorId) {
        return requireCreator(creatorId).getAvatarFileId();
    }

    /**
     * 加载创作者，不存在时抛出业务异常。
     *
     * @param creatorId 创作者 ID
     * @return 创作者
     */
    private Creator requireCreator(Long creatorId) {
        return creatorRepository.findById(creatorId)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CREATOR_NOT_FOUND));
    }

    /**
     * 分类转视图。
     *
     * @param category 分类
     * @return 分类视图
     */
    private CategoryView toView(Category category) {
        CategoryView view = new CategoryView();
        view.setId(category.getId());
        view.setParentId(category.getParentId());
        view.setName(category.getName());
        view.setCode(category.getCode());
        view.setContentTypeScope(category.getContentTypeScope());
        view.setSortNo(category.getSortNo());
        return view;
    }

    /**
     * 标签转视图。
     *
     * @param tag 标签
     * @return 标签视图
     */
    private TagView toView(Tag tag) {
        TagView view = new TagView();
        view.setId(tag.getId());
        view.setName(tag.getName());
        view.setTagType(tag.getTagType() == null ? null : tag.getTagType().name());
        view.setSortNo(tag.getSortNo());
        return view;
    }
}
