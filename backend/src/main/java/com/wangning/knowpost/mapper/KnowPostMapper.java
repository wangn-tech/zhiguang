package com.wangning.knowpost.mapper;

import com.wangning.knowpost.domain.KnowPost;
import com.wangning.knowpost.domain.KnowPostDetailRow;
import com.wangning.knowpost.domain.KnowPostFeedRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知文数据访问接口。
 */
@Mapper
public interface KnowPostMapper {

    /**
     * 插入一个新草稿。
     *
     * @param knowPost 已生成雪花 ID 的草稿
     * @return 受影响行数
     */
    int insertDraft(KnowPost knowPost);

    /**
     * 根据 ID 查询知文，包含已删除记录。
     *
     * @param id 知文 ID
     * @return 知文，不存在时返回 {@code null}
     */
    KnowPost findById(@Param("id") long id);

    /**
     * 更新作者知文的正文确认信息。
     *
     * @param knowPost 必须包含知文 ID、作者 ID 与待更新的正文信息
     * @return 受影响行数
     */
    int updateContent(KnowPost knowPost);

    /**
     * 更新作者知文的元数据。
     *
     * @param knowPost 必须包含知文 ID、作者 ID 与待更新字段
     * @return 受影响行数
     */
    int updateMetadata(KnowPost knowPost);

    /**
     * 将作者的知文发布。
     *
     * @param id 知文 ID
     * @param creatorId 作者用户 ID
     * @return 受影响行数
     */
    int publish(@Param("id") long id, @Param("creatorId") long creatorId);

    /**
     * 更新作者知文的置顶状态。
     *
     * @param id 知文 ID
     * @param creatorId 作者用户 ID
     * @param isTop 是否置顶
     * @return 受影响行数
     */
    int updateTop(
            @Param("id") long id,
            @Param("creatorId") long creatorId,
            @Param("isTop") boolean isTop
    );

    /**
     * 更新作者知文的可见性。
     *
     * @param id 知文 ID
     * @param creatorId 作者用户 ID
     * @param visible 可见性值
     * @return 受影响行数
     */
    int updateVisibility(
            @Param("id") long id,
            @Param("creatorId") long creatorId,
            @Param("visible") String visible
    );

    /**
     * 将作者知文标记为已删除。
     *
     * @param id 知文 ID
     * @param creatorId 作者用户 ID
     * @return 受影响行数
     */
    int softDelete(@Param("id") long id, @Param("creatorId") long creatorId);

    /**
     * 分页查询公开且已发布的 Feed。
     *
     * @param limit 最大查询数量
     * @param offset 偏移量
     * @return Feed 查询结果
     */
    List<KnowPostFeedRow> listFeedPublic(@Param("limit") int limit, @Param("offset") int offset);

    /**
     * 分页查询指定作者已发布的知文。
     *
     * @param creatorId 作者用户 ID
     * @param limit 最大查询数量
     * @param offset 偏移量
     * @return Feed 查询结果
     */
    List<KnowPostFeedRow> listMyPublished(
            @Param("creatorId") long creatorId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * 查询用户全部已发布知文的 ID，用于重建用户维度计数。
     *
     * @param creatorId 作者用户 ID
     * @return 已发布知文 ID 列表
     */
    List<Long> listPublishedIdsByCreator(@Param("creatorId") long creatorId);

    /**
     * 查询包含作者展示信息的知文详情。
     *
     * @param id 知文 ID
     * @return 详情查询结果，不存在时返回 {@code null}
     */
    KnowPostDetailRow findDetailById(@Param("id") long id);
}
