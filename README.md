🚀 Virality Engine - Spring Boot Microservice
A high-performance Spring Boot microservice that implements atomic Redis guardrails to prevent AI compute runaway in a social media platform with bot interactions.

📋 Table of Contents
Tech Stack
Features
Architecture
Prerequisites
Setup Instructions
API Endpoints
Thread Safety & Concurrency
Testing
Guardrail Rules
🛠️ Tech Stack
Java 17+
Spring Boot 3.2.0
MySQL 8.0 (Database)
Redis (Caching & Atomic Operations)
Spring Data JPA (ORM)
Spring Data Redis (Redis Integration)
Lombok (Boilerplate Reduction)
Maven (Build Tool)
✨ Features
Phase 1: Core API
✅ RESTful API endpoints for posts, comments, and likes
✅ JPA entities with MySQL persistence
✅ User and Bot author support
Phase 2: Redis Virality Engine
✅ Virality Score Calculation (Real-time Redis tracking)
Bot Reply: +1 point
Human Like: +20 points
Human Comment: +50 points
✅ Atomic Guardrails (Thread-safe concurrency protection)
Horizontal Cap: Max 100 bot replies per post
Vertical Cap: Max 20 levels deep in comment threads
Cooldown Cap: Bot cannot interact with same human within 10 minutes
Phase 3: Smart Notification System
✅ 15-minute Throttling: Prevents notification spam
✅ Pending Queue: Batches notifications using Redis Lists
✅ CRON Sweeper: Scheduled task (every 5 minutes) to send summarized notifications
🏗️ Architecture
┌─────────────────┐
│   REST API      │
│  (Controllers)  │
└────────┬────────┘
         │
┌────────▼────────┐      ┌──────────────┐
│   Services      │─────▶│   MySQL      │
│  (Business      │      │  (Persistent │
│   Logic)        │      │   Storage)   │
└────────┬────────┘      └──────────────┘
         │
┌────────▼────────┐
│  Redis Service  │
│   (Guardrails   │
│   & Caching)    │
└─────────────────┘
         │
┌────────▼────────┐
│     Redis       │
│   (In-Memory    │
│    Database)    │
└─────────────────┘
📦 Prerequisites
Before running this project, ensure you have:

Java 17 or higher

java -version
Maven 3.6+

mvn -version
MySQL 8.0+

Running on localhost:3306
Username: root
Password: root (or update in application.properties)
Redis Server

Running on localhost:6379
Install Redis:
# Ubuntu/Debian
sudo apt-get install redis-server
sudo systemctl start redis

# macOS (Homebrew)
brew install redis
brew services start redis

# Windows
# Download from: https://github.com/microsoftarchive/redis/releases
🚀 Setup Instructions
Step 1: Clone the Repository
git clone <your-repo-url>
cd virality-engine
Step 2: Configure MySQL
Start MySQL server
The database virality_db will be created automatically on first run
Update credentials in src/main/resources/application.properties if needed:
spring.datasource.username=root
spring.datasource.password=your_password
Step 3: Start Redis
# Check if Redis is running
redis-cli ping
# Should return: PONG

# If not running, start it:
sudo systemctl start redis  # Linux
brew services start redis   # macOS
Step 4: Build the Project
mvn clean install
Step 5: Run the Application
mvn spring-boot:run
The application will start on http://localhost:8080

Step 6: Verify Setup
# Check application health
curl http://localhost:8080/api/posts

# Should return empty list or error (no posts yet)
📡 API Endpoints
Posts
Method	Endpoint	Description
POST	/api/posts	Create a new post
GET	/api/posts/{postId}	Get post details
GET	/api/posts/{postId}/virality	Get virality score
POST	/api/posts/{postId}/like	Like a post
POST	/api/posts/{postId}/comments	Add comment to post
Example Requests
Create Post:

curl -X POST http://localhost:8080/api/posts \
  -H "Content-Type: application/json" \
  -d '{
    "authorId": 1,
    "authorType": "USER",
    "content": "My first post!"
  }'
Add Bot Comment:

curl -X POST http://localhost:8080/api/posts/1/comments \
  -H "Content-Type: application/json" \
  -d '{
    "authorId": 1,
    "authorType": "BOT",
    "content": "Interesting post!",
    "parentCommentId": null
  }'
Like Post:

curl -X POST http://localhost:8080/api/posts/1/like \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 2
  }'
🔒 Thread Safety & Concurrency
The Challenge
When 200 bots try to comment on a post simultaneously, the system must ensure exactly 100 comments are allowed (not 101, not 99). This requires atomic operations.

Solution: Redis Atomic Operations
1. Horizontal Cap (Max 100 Bot Replies)
public void checkAndIncrementBotCount(Long postId) {
    String key = "post:" + postId + ":bot_count";
    
    // ATOMIC INCREMENT - Thread-safe
    Long currentCount = redisTemplate.opsForValue().increment(key, 1);
    
    if (currentCount > MAX_BOT_REPLIES_PER_POST) {
        // Rollback if exceeded
        redisTemplate.opsForValue().decrement(key, 1);
        throw new GuardrailViolationException("Horizontal Cap Exceeded");
    }
}
Why This is Thread-Safe:

✅ increment() is an atomic Redis operation (INCR command)
✅ Redis processes commands sequentially (single-threaded event loop)
✅ Even with 200 concurrent requests, Redis executes them one-by-one
✅ The counter increments atomically: no race conditions
Race Condition Test:

Thread 1: increment(99) → returns 100 ✅
Thread 2: increment(100) → returns 101 → rollback to 100 ✅
Thread 3: increment(100) → returns 101 → rollback to 100 ✅
...
Thread 200: increment(100) → returns 101 → rollback to 100 ✅
Result: Exactly 100 comments, guaranteed.

2. Cooldown Cap (10-Minute Bot-Human Interaction)
public void checkAndSetBotCooldown(Long botId, Long humanId) {
    String key = "cooldown:bot_" + botId + ":human_" + humanId;
    
    // ATOMIC SET-IF-NOT-EXISTS with TTL
    Boolean wasSet = redisTemplate.opsForValue()
        .setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
    
    if (Boolean.FALSE.equals(wasSet)) {
        throw new GuardrailViolationException("Cooldown violated");
    }
}
Why This is Thread-Safe:

✅ setIfAbsent() uses Redis SETNX command (SET if Not eXists)
✅ Returns true only for the first request that sets the key
✅ All subsequent requests within 10 minutes get false
✅ The key automatically expires after 10 minutes (TTL)
3. Vertical Cap (Max 20 Depth)
public void checkVerticalCap(int depthLevel) {
    if (depthLevel > MAX_COMMENT_DEPTH) {
        throw new GuardrailViolationException("Vertical Cap Exceeded");
    }
}
Why This is Thread-Safe:

✅ Depth is calculated from database state (parent comment's depth)
✅ Protected by Spring's @Transactional with proper isolation level
✅ No shared state between threads
Statelessness Guarantee
❌ No HashMap or in-memory counters in Java
❌ No static variables storing state
✅ All state in Redis (can survive application restarts)
✅ Database as source of truth for content
Transaction Flow
1. Client Request → Controller
2. Redis Guardrail Check (ATOMIC)
   ├─ Pass → Continue
   └─ Fail → Return 429 (No DB write)
3. Database Transaction (JPA)
   └─ Commit only if Redis allowed
4. Response to Client
🧪 Testing
Manual Testing with Postman
Import the Postman collection: Virality_Engine_API.postman_collection.json
Run requests in order to test features
Use request #9 to test horizontal cap (after 100 bot comments)
Use request #10 to test cooldown cap
Concurrency Test (200 Concurrent Requests)
# Install Apache Bench (if not installed)
sudo apt-get install apache2-utils  # Linux
brew install httpd                   # macOS

# Create test payload
echo '{
  "authorId": 1,
  "authorType": "BOT",
  "content": "Concurrent test",
  "parentCommentId": null
}' > bot_comment.json

# Fire 200 concurrent requests
ab -n 200 -c 200 -p bot_comment.json \
   -T application/json \
   http://localhost:8080/api/posts/1/comments

# Verify count in Redis
redis-cli GET "post:1:bot_count"
# Should return: "100"

# Verify count in MySQL
mysql -u root -p -e "SELECT COUNT(*) FROM virality_db.comments WHERE post_id=1 AND author_type='BOT';"
# Should return: 100
Notification CRON Test
Create a post by a human user (ID 1)
Add 5 bot comments within 5 minutes
Wait for CRON sweeper (runs every 5 minutes)
Check console logs for summarized notification:
📱 Push Notification Sent to User 1: Summarized Push Notification: Bot 1 and 4 others interacted with your posts
🛡️ Guardrail Rules
Guardrail	Limit	Implementation
Horizontal Cap	100 bot replies/post	Redis INCR (atomic)
Vertical Cap	20 levels deep	Depth calculation + validation
Cooldown Cap	10 minutes (bot-human)	Redis SETNX with TTL
Notification Throttle	15 minutes/user	Redis key with TTL + List
Virality Score Formula
Score = (Bot Replies × 1) + (Human Likes × 20) + (Human Comments × 50)
📂 Project Structure
virality-engine/
├── src/main/java/com/assignment/virality/
│   ├── entity/           # JPA Entities (User, Bot, Post, Comment)
│   ├── repository/       # Spring Data JPA Repositories
│   ├── service/          # Business Logic
│   │   ├── RedisService.java          # Atomic guardrail operations
│   │   ├── NotificationService.java   # Smart batching + CRON
│   │   ├── PostService.java
│   │   └── CommentService.java
│   ├── controller/       # REST API Controllers
│   ├── dto/              # Data Transfer Objects
│   ├── exception/        # Custom Exceptions + Handler
│   ├── config/           # Redis Configuration
│   └── ViralityEngineApplication.java
├── src/main/resources/
│   └── application.properties
├── pom.xml
├── README.md
└── Virality_Engine_API.postman_collection.json
🐛 Troubleshooting
Redis Connection Error
# Check if Redis is running
redis-cli ping

# Start Redis
sudo systemctl start redis  # Linux
brew services start redis   # macOS
MySQL Connection Error
# Check MySQL status
sudo systemctl status mysql  # Linux

# Verify credentials in application.properties
# Default: root/root on localhost:3306
Port 8080 Already in Use
# Kill process using port 8080
sudo lsof -i :8080
sudo kill -9 <PID>

# Or change port in application.properties
server.port=8081
Key Design Decisions
Redis as Guardrail Gatekeeper

All rate limits checked BEFORE database writes
Prevents wasted transactions
Atomic Operations Over Distributed Locks

Simpler and faster than Redlock
Sufficient for single Redis instance
Notification Batching

Reduces user churn from spam
CRON sweeper ensures no notifications are lost
Stateless Design

App can scale horizontally
Redis holds all transient state
👨‍💻 Author
Backend Engineering Assignment - Spring Boot Grid07 Intern

📄 License
This project is created for educational/assessment purposes.

Future Enhancements
 Add Redis Cluster support for high availability
 Implement WebSocket for real-time notifications
 Add metrics and monitoring (Prometheus/Grafana)
 Implement user authentication (JWT)
 Add comprehensive unit and integration tests
 Deploy to Kubernetes cluster
