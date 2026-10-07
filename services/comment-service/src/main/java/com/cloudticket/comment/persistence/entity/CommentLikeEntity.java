package com.cloudticket.comment.persistence.entity;
import com.baomidou.mybatisplus.annotation.TableName; import java.time.Instant;
@TableName("comment_like_record") public class CommentLikeEntity { private String commentId; private String userId; private Instant createdAt; public String getCommentId(){return commentId;} public void setCommentId(String v){commentId=v;} public String getUserId(){return userId;} public void setUserId(String v){userId=v;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;} }
