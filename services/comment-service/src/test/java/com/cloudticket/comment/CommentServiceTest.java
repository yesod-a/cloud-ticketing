package com.cloudticket.comment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.cloudticket.comment.persistence.entity.CommentEntity;
import com.cloudticket.comment.persistence.mapper.CommentMapper;
import com.cloudticket.comment.service.CommentService;
import org.junit.jupiter.api.Test;

class CommentServiceTest {
  private final CommentMapper comments = mock(CommentMapper.class);
  private final CommentService service = new CommentService(comments);

  @Test
  void acceptsACommentWithoutCheckingForAPaidOrder() {
    when(comments.insert(org.mockito.ArgumentMatchers.any(CommentEntity.class))).thenReturn(1);

    CommentEntity created = service.create("user-1", "activity-1", null, "还没买票也能评论");

    assertEquals("还没买票也能评论", created.getContent());
    verify(comments).insert(org.mockito.ArgumentMatchers.any(CommentEntity.class));
  }

  @Test
  void replyCreatesAChildAndIncrementsTheRootReplyCount() {
    CommentEntity parent = new CommentEntity();
    parent.setId("comment-1");
    parent.setActivityId("activity-1");
    when(comments.selectById("comment-1")).thenReturn(parent);
    when(comments.insert(org.mockito.ArgumentMatchers.any(CommentEntity.class))).thenReturn(1);

    CommentEntity reply = service.reply("user-2", "comment-1", "同意");

    assertEquals("activity-1", reply.getActivityId());
    assertEquals("comment-1", reply.getParentId());
    assertEquals("同意", reply.getContent());
    verify(comments).incrementReply("comment-1");
  }

  @Test
  void replyToAnotherReplyStillAttachesToTheRootComment() {
    CommentEntity parent = new CommentEntity();
    parent.setId("reply-1");
    parent.setActivityId("activity-1");
    parent.setParentId("comment-1");
    when(comments.selectById("reply-1")).thenReturn(parent);
    when(comments.insert(org.mockito.ArgumentMatchers.any(CommentEntity.class))).thenReturn(1);

    CommentEntity reply = service.reply("user-3", "reply-1", "补充回复");

    assertEquals("comment-1", reply.getParentId());
    verify(comments).incrementReply("comment-1");
    verify(comments, times(0)).incrementReply("reply-1");
  }
}
