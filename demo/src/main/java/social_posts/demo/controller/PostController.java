package social_posts.demo.controller;


import social_posts.demo.dto.CreateCommentRequest;
import social_posts.demo.dto.CreatePostRequest;
import social_posts.demo.dto.LikePostRequest;
import social_posts.demo.entity.Comment;
import social_posts.demo.entity.Post;
import social_posts.demo.service.CommentService;
import social_posts.demo.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;
    private final CommentService commentService;

    /**
     * POST /api/posts - Create a new post
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPost(@Valid @RequestBody CreatePostRequest request) {
        log.info("Received request to create post: {}", request);

        Post post = postService.createPost(request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Post created successfully");
        response.put("post", post);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/posts/{postId}/comments - Add a comment to a post
     */
    @PostMapping("/{postId}/comments")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request) {

        log.info("Received request to add comment to post {}: {}", postId, request);

        Comment comment = commentService.addComment(postId, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Comment added successfully");
        response.put("comment", comment);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/posts/{postId}/like - Like a post
     */
    @PostMapping("/{postId}/like")
    public ResponseEntity<Map<String, Object>> likePost(
            @PathVariable Long postId,
            @Valid @RequestBody LikePostRequest request) {

        log.info("Received request to like post {} by user {}", postId, request.getUserId());

        postService.likePost(postId, request.getUserId());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Post liked successfully");
        response.put("viralityScore", postService.getViralityScore(postId));

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/posts/{postId} - Get post details
     */
    @GetMapping("/{postId}")
    public ResponseEntity<Map<String, Object>> getPost(@PathVariable Long postId) {
        Post post = postService.getPost(postId);
        Long viralityScore = postService.getViralityScore(postId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("post", post);
        response.put("viralityScore", viralityScore);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/posts/{postId}/virality - Get virality score
     */
    @GetMapping("/{postId}/virality")
    public ResponseEntity<Map<String, Object>> getViralityScore(@PathVariable Long postId) {
        Long score = postService.getViralityScore(postId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("postId", postId);
        response.put("viralityScore", score);

        return ResponseEntity.ok(response);
    }
}
