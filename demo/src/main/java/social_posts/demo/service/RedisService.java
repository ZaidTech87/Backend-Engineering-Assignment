package social_posts.demo.service;


import social_posts.demo.exception.GuardrailViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Constants for Guardrails
    private static final int MAX_BOT_REPLIES_PER_POST = 100;
    private static final int MAX_COMMENT_DEPTH = 20;
    private static final int BOT_COOLDOWN_MINUTES = 10;
    private static final int NOTIFICATION_COOLDOWN_MINUTES = 15;

    // ===== VIRALITY SCORE MANAGEMENT =====

    public void incrementViralityScore(Long postId, int points) {
        String key = getViralityScoreKey(postId);
        redisTemplate.opsForValue().increment(key, points);
        log.debug("Virality score for post {} incremented by {} points", postId, points);
    }

    public Long getViralityScore(Long postId) {
        String key = getViralityScoreKey(postId);
        Object score = redisTemplate.opsForValue().get(key);
        return score != null ? Long.parseLong(score.toString()) : 0L;
    }

    // ===== HORIZONTAL CAP: Max 100 bot replies per post =====

    public void checkAndIncrementBotCount(Long postId) {
        String key = getBotCountKey(postId);

        // Atomic increment and get
        Long currentCount = redisTemplate.opsForValue().increment(key, 1);

        if (currentCount > MAX_BOT_REPLIES_PER_POST) {
            // Rollback the increment
            redisTemplate.opsForValue().decrement(key, 1);
            throw new GuardrailViolationException(
                    String.format("Horizontal Cap Exceeded: Post %d already has %d bot replies (max: %d)",
                            postId, MAX_BOT_REPLIES_PER_POST, MAX_BOT_REPLIES_PER_POST)
            );
        }

        log.debug("Bot count for post {} is now {}", postId, currentCount);
    }

    public Long getBotCount(Long postId) {
        String key = getBotCountKey(postId);
        Object count = redisTemplate.opsForValue().get(key);
        return count != null ? Long.parseLong(count.toString()) : 0L;
    }

    // ===== VERTICAL CAP: Max 20 levels deep =====

    public void checkVerticalCap(int depthLevel) {
        if (depthLevel > MAX_COMMENT_DEPTH) {
            throw new GuardrailViolationException(
                    String.format("Vertical Cap Exceeded: Comment depth %d exceeds maximum of %d",
                            depthLevel, MAX_COMMENT_DEPTH)
            );
        }
    }

    // ===== COOLDOWN CAP: Bot cannot interact with same human within 10 minutes =====

    public void checkAndSetBotCooldown(Long botId, Long humanId) {
        String key = getCooldownKey(botId, humanId);

        // Try to set key with NX (only if not exists) and expiry
        Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(key, "1", BOT_COOLDOWN_MINUTES, TimeUnit.MINUTES);

        if (Boolean.FALSE.equals(wasSet)) {
            throw new GuardrailViolationException(
                    String.format("Cooldown Cap Violated: Bot %d cannot interact with Human %d within %d minutes",
                            botId, humanId, BOT_COOLDOWN_MINUTES)
            );
        }

        log.debug("Cooldown set for Bot {} and Human {} for {} minutes", botId, humanId, BOT_COOLDOWN_MINUTES);
    }

    // ===== NOTIFICATION THROTTLING =====

    public boolean checkNotificationCooldown(Long userId) {
        String key = getNotificationCooldownKey(userId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void setNotificationCooldown(Long userId) {
        String key = getNotificationCooldownKey(userId);
        redisTemplate.opsForValue().set(key, "1", NOTIFICATION_COOLDOWN_MINUTES, TimeUnit.MINUTES);
        log.debug("Notification cooldown set for user {} for {} minutes", userId, NOTIFICATION_COOLDOWN_MINUTES);
    }

    public void addPendingNotification(Long userId, String message) {
        String key = getPendingNotificationsKey(userId);
        redisTemplate.opsForList().rightPush(key, message);
        log.debug("Added pending notification for user {}: {}", userId, message);
    }

    public List<Object> getPendingNotifications(Long userId) {
        String key = getPendingNotificationsKey(userId);
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            return List.of();
        }
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    public void clearPendingNotifications(Long userId) {
        String key = getPendingNotificationsKey(userId);
        redisTemplate.delete(key);
        log.debug("Cleared pending notifications for user {}", userId);
    }

    // Get all user IDs with pending notifications
    public Set<String> getAllUsersWithPendingNotifications() {
        return redisTemplate.keys("user:*:pending_notifs");
    }

    // ===== REDIS KEY HELPERS =====

    private String getViralityScoreKey(Long postId) {
        return "post:" + postId + ":virality_score";
    }

    private String getBotCountKey(Long postId) {
        return "post:" + postId + ":bot_count";
    }

    private String getCooldownKey(Long botId, Long humanId) {
        return "cooldown:bot_" + botId + ":human_" + humanId;
    }

    private String getNotificationCooldownKey(Long userId) {
        return "user:" + userId + ":notification_cooldown";
    }

    private String getPendingNotificationsKey(Long userId) {
        return "user:" + userId + ":pending_notifs";
    }
}
