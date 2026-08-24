package com.wangning.relation.mapper;

import com.wangning.relation.domain.RelationListItem;
import com.wangning.relation.domain.UserRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 用户关注关系的数据访问接口。
 *
 * <p>{@code following} 为正向关系表，{@code follower} 为反向查询表。两张表的同步
 * 由后续的关系服务与异步事件处理器负责，本接口只封装单表操作。</p>
 */
@Mapper
public interface RelationMapper {

    /**
     * 创建或恢复一条正向关注关系。
     *
     * <p>重复用户对会恢复为有效关系，并以传入时间刷新排序。MySQL 在重复键更新时
     * 可能返回 {@code 2}，调用方应以大于 {@code 0} 判断写入是否成功。</p>
     *
     * @param relation 正向关系，必须包含 ID、发起者、目标用户和时间
     * @return 受影响行数
     */
    int upsertFollowing(UserRelation relation);

    /**
     * 将正向关注关系标记为已取消。
     *
     * @param fromUserId 关注发起者用户 ID
     * @param toUserId 被关注者用户 ID
     * @param updatedAt 状态更新时间
     * @return 原本为有效关系时返回 {@code 1}，否则返回 {@code 0}
     */
    int deactivateFollowing(
            @Param("fromUserId") long fromUserId,
            @Param("toUserId") long toUserId,
            @Param("updatedAt") Instant updatedAt
    );

    /**
     * 创建或恢复一条反向粉丝关系。
     *
     * @param relation 关系对象，{@code fromUserId} 为粉丝，{@code toUserId} 为被关注者
     * @return 受影响行数
     */
    int upsertFollower(UserRelation relation);

    /**
     * 将反向粉丝关系标记为已取消。
     *
     * @param toUserId 被关注者用户 ID
     * @param fromUserId 粉丝用户 ID
     * @param updatedAt 状态更新时间
     * @return 原本为有效关系时返回 {@code 1}，否则返回 {@code 0}
     */
    int deactivateFollower(
            @Param("toUserId") long toUserId,
            @Param("fromUserId") long fromUserId,
            @Param("updatedAt") Instant updatedAt
    );

    /**
     * 查询一条正向关系，包含已取消记录。
     *
     * @param fromUserId 关注发起者用户 ID
     * @param toUserId 被关注者用户 ID
     * @return 关系记录，不存在时返回 {@code null}
     */
    UserRelation findFollowing(
            @Param("fromUserId") long fromUserId,
            @Param("toUserId") long toUserId
    );

    /**
     * 查询一条反向关系，包含已取消记录。
     *
     * @param toUserId 被关注者用户 ID
     * @param fromUserId 粉丝用户 ID
     * @return 关系记录，不存在时返回 {@code null}
     */
    UserRelation findFollower(
            @Param("toUserId") long toUserId,
            @Param("fromUserId") long fromUserId
    );

    /**
     * 判断用户是否有效关注目标用户。
     *
     * @param fromUserId 关注发起者用户 ID
     * @param toUserId 被关注者用户 ID
     * @return 有效关注时返回 {@code true}
     */
    boolean existsFollowing(
            @Param("fromUserId") long fromUserId,
            @Param("toUserId") long toUserId
    );

    /**
     * 按时间倒序读取关注列表。
     *
     * @param fromUserId 用户 ID
     * @param limit 最大返回数量
     * @param offset 偏移量
     * @return 另一方用户 ID 和关系时间
     */
    List<RelationListItem> listFollowings(
            @Param("fromUserId") long fromUserId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * 按时间倒序读取粉丝列表。
     *
     * @param toUserId 用户 ID
     * @param limit 最大返回数量
     * @param offset 偏移量
     * @return 另一方用户 ID 和关系时间
     */
    List<RelationListItem> listFollowers(
            @Param("toUserId") long toUserId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * 按时间游标读取关注列表。
     *
     * @param fromUserId 用户 ID
     * @param cursor 上一页最后一条关系的创建时间
     * @param limit 最大返回数量
     * @return 下一页关系排序信息
     */
    List<RelationListItem> listFollowingsBefore(
            @Param("fromUserId") long fromUserId,
            @Param("cursor") Instant cursor,
            @Param("limit") int limit
    );

    /**
     * 按时间游标读取粉丝列表。
     *
     * @param toUserId 用户 ID
     * @param cursor 上一页最后一条关系的创建时间
     * @param limit 最大返回数量
     * @return 下一页关系排序信息
     */
    List<RelationListItem> listFollowersBefore(
            @Param("toUserId") long toUserId,
            @Param("cursor") Instant cursor,
            @Param("limit") int limit
    );

    /**
     * 统计用户的有效关注数。
     *
     * @param fromUserId 用户 ID
     * @return 有效关注数
     */
    long countFollowings(@Param("fromUserId") long fromUserId);

    /**
     * 统计用户的有效粉丝数。
     *
     * @param toUserId 用户 ID
     * @return 有效粉丝数
     */
    long countFollowers(@Param("toUserId") long toUserId);
}
