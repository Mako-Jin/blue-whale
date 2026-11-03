package com.bluewhale.video.application.support;

import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.Creator;
import com.bluewhale.video.domain.model.Tag;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.CreatorRepository;
import com.bluewhale.video.domain.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用户侧内容视图批量组装器。
 *
 * <p>批量查询标签名称与创作者昵称，避免逐条回表；公开视图不暴露任何文件 ID，
 * 封面统一走 {@code /video/content/{id}/cover} 代理。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class PublicContentViewAssembler {

    private final ContentRepository contentRepository;

    private final TagRepository tagRepository;

    private final CreatorRepository creatorRepository;

    /**
     * 批量组装用户侧内容视图。
     *
     * @param contents 内容列表
     * @return 用户侧视图列表；入参为空返回空列表
     */
    public List<PublicContentView> assemble(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<Long>> tagMap = contentRepository.findTagIdsByContentIds(idsOf(contents));
        Map<Long, String> tagNames = tagNameMap(tagMap.values());
        Map<Long, String> creatorNames = creatorNameMap(contents);
        List<PublicContentView> views = new ArrayList<>(contents.size());
        for (Content content : contents) {
            List<String> names = tagMap.getOrDefault(content.getId(), Collections.emptyList()).stream()
                    .map(tagNames::get).filter(name -> name != null).toList();
            views.add(ContentAssembler.toPublic(content, names, creatorNames.get(content.getCreatorId())));
        }
        return views;
    }

    /**
     * 组装单个用户侧内容视图。
     *
     * @param content 内容
     * @return 用户侧视图
     */
    public PublicContentView assemble(Content content) {
        return assemble(List.of(content)).get(0);
    }

    /**
     * 查询内容标签名称。
     *
     * @param contentId 内容 ID
     * @return 标签名称列表
     */
    public List<String> tagNames(Long contentId) {
        List<Long> tagIds = contentRepository.findTagIds(contentId);
        if (tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        return tagRepository.findByIds(tagIds).stream().map(Tag::getName).toList();
    }

    /**
     * 提取内容 ID 集合。
     *
     * @param contents 内容列表
     * @return 内容 ID 集合
     */
    private List<Long> idsOf(List<Content> contents) {
        return contents.stream().map(Content::getId).toList();
    }

    /**
     * 批量把标签 ID 映射为名称。
     *
     * @param idLists 标签 ID 集合
     * @return 标签 ID → 名称
     */
    private Map<Long, String> tagNameMap(Collection<List<Long>> idLists) {
        List<Long> ids = idLists.stream().flatMap(List::stream).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return tagRepository.findByIds(ids).stream().collect(Collectors.toMap(Tag::getId, Tag::getName));
    }

    /**
     * 批量查询创作者昵称。
     *
     * @param contents 内容列表
     * @return 创作者 ID → 昵称
     */
    private Map<Long, String> creatorNameMap(List<Content> contents) {
        List<Long> creatorIds = contents.stream().map(Content::getCreatorId)
                .filter(id -> id != null && id > 0).distinct().toList();
        if (creatorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return creatorIds.stream().map(creatorRepository::findById)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(Creator::getId, Creator::getDisplayName));
    }
}
