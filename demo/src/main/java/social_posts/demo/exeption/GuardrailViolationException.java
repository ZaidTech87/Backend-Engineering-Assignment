package social_posts.demo.exeption;

public class GuardrailViolationException extends RuntimeException {
    public GuardrailViolationException(String message) {
        super(message);
    }
}
