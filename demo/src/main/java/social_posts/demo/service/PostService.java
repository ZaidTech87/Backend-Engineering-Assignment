package social_posts.demo.service;


import social_posts.demo.dto.CreatePostRequest;
import social_posts.demo.entity.Post;
import social_posts.demo.exception.ResourceNotFoundException;
import social_posts.demo.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final RedisService redisService;

    @Transactional
    public Post createPost(CreatePostRequest request) {
        Post post = new Post();
        post.setAuthorId(request.getAuthorId());
        post.setAuthorType(request.getAuthorType());
        post.setContent(request.getContent());

        Post savedPost = postRepository.save(post);
        log.info("Created post with ID: {} by {} {}", savedPost.getId(), request.getAuthorType(), request.getAuthorId());

        return savedPost;
    }

    @Transactional
    public void likePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with ID: " + postId));

        // Increment virality score by 20 points for human like
        redisService.incrementViralityScore(postId, 20);

        log.info("User {} liked post {}. Virality score: +20 points", userId, postId);

        // Note: In a real system, you might want to store likes in DB as well
        // For this assignment, we're focusing on Redis virality tracking
    }

    public Post getPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with ID: " + postId));
    }

    public Long getViralityScore(Long postId) {
        return redisService.getViralityScore(postId);
    }
}
