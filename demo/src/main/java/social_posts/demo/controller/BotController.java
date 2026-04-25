package social_posts.demo.controller;

import social_posts.demo.entity.Bot;
import social_posts.demo.repository.BotRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bots")
@RequiredArgsConstructor
public class BotController {

    private final BotRepository botRepository;

    /**
     * POST /api/bots - Create a new bot
     */
    @PostMapping
    public ResponseEntity<Bot> createBot(@Valid @RequestBody Bot bot) {
        Bot savedBot = botRepository.save(bot);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedBot);
    }

    /**
     * GET /api/bots - Get all bots
     */
    @GetMapping
    public ResponseEntity<List<Bot>> getAllBots() {
        List<Bot> bots = botRepository.findAll();
        return ResponseEntity.ok(bots);
    }

    /**
     * GET /api/bots/{id} - Get bot by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Bot> getBotById(@PathVariable Long id) {
        Bot bot = botRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bot not found"));
        return ResponseEntity.ok(bot);
    }
}
