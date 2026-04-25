package social_posts.demo.dto;



import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LikePostRequest {

    @NotNull(message = "User ID is required")
    private Long userId;
}
