package social_posts.demo.service;



import social_posts.demo.entity.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final RedisService redisService;

    /**
     * Handle notification for bot interaction
     * Implements smart batching with 15-minute throttle
     */
    public void handleBotInteraction(Long postAuthorId, Long botId, String interactionType, Post.AuthorType postAuthorType) {
        // Only notify human users
        if (postAuthorType != Post.AuthorType.USER) {
            return;
        }

        boolean isOnCooldown = redisService.checkNotificationCooldown(postAuthorId);

        if (isOnCooldown) {
            // User received notification recently, add to pending queue
            String notification = String.format("Bot %d %s your post", botId, interactionType);
            redisService.addPendingNotification(postAuthorId, notification);
            log.info("Added to pending notifications queue for user {}", postAuthorId);
        } else {
            // Send immediate notification
            sendPushNotification(postAuthorId, String.format("Bot %d %s your post", botId, interactionType));
            redisService.setNotificationCooldown(postAuthorId);
        }
    }

    /**
     * CRON Sweeper - runs every 5 minutes to process pending notifications
     * In production, this would run every 15 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    public void sweepPendingNotifications() {
        log.info("=== CRON SWEEPER STARTED ===");

        Set<String> userKeys = redisService.getAllUsersWithPendingNotifications();

        if (userKeys == null || userKeys.isEmpty()) {
            log.info("No pending notifications to process");
            return;
        }

        for (String userKey : userKeys) {
            try {
                // Extract user ID from key format: "user:{id}:pending_notifs"
                Long userId = extractUserIdFromKey(userKey);

                List<Object> pendingNotifications = redisService.getPendingNotifications(userId);

                if (!pendingNotifications.isEmpty()) {
                    int count = pendingNotifications.size();

                    // Get first bot from the list
                    String firstNotification = pendingNotifications.get(0).toString();

                    if (count == 1) {
                        sendPushNotification(userId, firstNotification);
                    } else {
                        String summarized = String.format("Summarized Push Notification: %s and %d others interacted with your posts",
                                firstNotification.replace(" your post", ""), count - 1);
                        sendPushNotification(userId, summarized);
                    }

                    // Clear pending notifications
                    redisService.clearPendingNotifications(userId);
                }
            } catch (Exception e) {
                log.error("Error processing notifications for key {}: {}", userKey, e.getMessage());
            }
        }

        log.info("=== CRON SWEEPER COMPLETED ===");
    }

    /**
     * Simulates sending a push notification (logs to console)
     */
    private void sendPushNotification(Long userId, String message) {
        log.info("📱 Push Notification Sent to User {}: {}", userId, message);
    }

    /**
     * Extract user ID from Redis key format
     */
    private Long extractUserIdFromKey(String key) {
        // Format: "user:{id}:pending_notifs"
        String[] parts = key.split(":");
        return Long.parseLong(parts[1]);
    }
}
