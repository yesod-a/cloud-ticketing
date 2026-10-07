package com.cloudticket.comment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudticket.comment.persistence.entity.CommentLikeEntity;
import com.cloudticket.comment.persistence.mapper.CommentLikeMapper;
import com.cloudticket.comment.persistence.mapper.CommentMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentLikeService {
  private final StringRedisTemplate redis;
  private final CommentLikeMapper likes;
  private final CommentMapper comments;

  public CommentLikeService(StringRedisTemplate redis, CommentLikeMapper likes, CommentMapper comments) {
    this.redis = redis;
    this.likes = likes;
    this.comments = comments;
  }

  @Transactional
  public Result like(String commentId, String userId) {
    CommentLikeEntity row = new CommentLikeEntity();
    row.setCommentId(commentId);
    row.setUserId(userId);
    row.setCreatedAt(Instant.now());
    try {
      likes.insert(row);
    } catch (DuplicateKeyException alreadyLiked) {
      return new Result(true, count(commentId));
    }
    redis.opsForSet().add("comment:likes:" + commentId, userId);
    comments.update(null, Wrappers.<com.cloudticket.comment.persistence.entity.CommentEntity>update()
        .eq("id", commentId).setSql("like_count=like_count+1"));
    redis.opsForZSet().incrementScore("comment:likes:dirty", commentId, 1);
    return new Result(true, count(commentId));
  }

  @Transactional
  public Result unlike(String commentId, String userId) {
    int removed = likes.deleteOne(commentId, userId);
    if (removed > 0) {
      redis.opsForSet().remove("comment:likes:" + commentId, userId);
      comments.update(null, Wrappers.<com.cloudticket.comment.persistence.entity.CommentEntity>update()
          .eq("id", commentId).setSql("like_count=GREATEST(0,like_count-1)"));
      redis.opsForZSet().incrementScore("comment:likes:dirty", commentId, -1);
    }
    return new Result(false, count(commentId));
  }

  public List<String> likedCommentIds(String userId, List<String> commentIds) {
    if (commentIds == null || commentIds.isEmpty()) return List.of();
    return likes.selectList(Wrappers.<CommentLikeEntity>query().select("comment_id")
        .eq("user_id", userId).in("comment_id", commentIds)).stream()
        .map(CommentLikeEntity::getCommentId).toList();
  }

  private int count(String id) {
    var row = comments.selectById(id);
    return row == null ? 0 : Math.max(0, row.getLikeCount() == null ? 0 : row.getLikeCount());
  }

  public record Result(boolean liked, int likeCount) {}
}
