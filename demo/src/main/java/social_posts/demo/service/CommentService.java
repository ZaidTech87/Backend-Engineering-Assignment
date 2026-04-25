package social_posts.demo.service;



import social_posts.demo.dto.CreateCommentRequest;
import social_posts.demo.entity.Comment;
import social_posts.demo.entity.Post;
import social_posts.demo.exception.ResourceNotFoundException;
import social_posts.demo.repository.CommentRepository;
import social_posts.demo.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final RedisService redisService;
    private final NotificationService notificationService;

    @Transactional
    public Comment addComment(Long postId, CreateCommentRequest request) {
        // Verify post exists
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with ID: " + postId));

        // Calculate depth level
        int depthLevel = 0;
        Comment parentComment = null;

        if (request.getParentCommentId() != null) {
            parentComment = commentRepository.findById(request.getParentCommentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found with ID: " + request.getParentCommentId()));
            depthLevel = parentComment.getDepthLevel() + 1;
        }

        // GUARDRAIL CHECKS FOR BOT COMMENTS
        if (request.getAuthorType() == Post.AuthorType.BOT) {
            // 1. VERTICAL CAP: Check depth level (max 20)
            redisService.checkVerticalCap(depthLevel);

            // 2. HORIZONTAL CAP: Check bot count (max 100 per post) - ATOMIC
            redisService.checkAndIncrementBotCount(postId);

            // 3. COOLDOWN CAP: Check if bot interacted with this human recently
            if (post.getAuthorType() == Post.AuthorType.USER) {
                redisService.checkAndSetBotCooldown(request.getAuthorId(), post.getAuthorId());
            }

            // Update virality score (+1 for bot reply)
            redisService.incrementViralityScore(postId, 1);

            // Handle notification for post author (if human)
            notificationService.handleBotInteraction(post.getAuthorId(), request.getAuthorId(), "replied to", post.getAuthorType());
        } else {
            // Human comment
            // Update virality score (+50 for human comment)
            redisService.incrementViralityScore(postId, 50);
        }

        // Create and save comment
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setParentCommentId(request.getParentCommentId());
        comment.setAuthorId(request.getAuthorId());
        comment.setAuthorType(request.getAuthorType());
        comment.setContent(request.getContent());
        comment.setDepthLevel(depthLevel);

        Comment savedComment = commentRepository.save(comment);

        log.info("Created comment ID: {} on post {} by {} {} (depth: {})",
                savedComment.getId(), postId, request.getAuthorType(), request.getAuthorId(), depthLevel);

        return savedComment;
    }

    public Comment getComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with ID: " + commentId));
    }
}
