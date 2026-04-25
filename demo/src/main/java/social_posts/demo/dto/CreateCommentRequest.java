package social_posts.demo.dto;


import social_posts.demo.entity.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCommentRequest {

    @NotNull(message = "Author ID is required")
    private Long authorId;

    @NotNull(message = "Author type is required")
    private Post.AuthorType authorType;

    @NotBlank(message = "Content is required")
    private String content;

    private Long parentCommentId; // null for top-level comment
}
