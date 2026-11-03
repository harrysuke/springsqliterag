# Spring Boot (Kotlin) + JTE + Axios + MyBatis + RAG

A modern, lightweight full-stack web application starter using **Spring Boot**, **Kotlin**, **JTE (Java Template Engine)**, **MyBatis**, and frontend interactions powered by **Axios** loaded straight from a CDN. Now with an integrated **RAG (Retrieval-Augmented Generation) AI Chat** layer powered by **Ollama** — no external API keys required.

This stack eliminates heavy frontend build pipelines (like Webpack, Vite, or npm dependencies) by rendering fast, type-safe HTML templates on the server and handling dynamic page interactions using Axios served from jsDelivr. The RAG intelligence layer runs entirely locally via Docker.

> 📘 **Technical documentation:** see [TECHNICAL.md](TECHNICAL.md) for the system architecture, the SQLite entity diagram, the HTTP API reference, the security model, and the Docker/CI-CD setup.
> 🤖 **RAG Design:** see [RAG_DESIGN.md](RAG_DESIGN.md) for the AI/ML intelligence layer architecture, data flow, and integration points.

## 🚀 Features
* **Kotlin Backend:** Clean, idiomatic backend development using Spring Boot and Kotlin coroutines/idioms.
* **MyBatis Persistence:** Hand-written SQL in XML mappers — no ORM magic, no string concatenation. Dynamic `<if>` filters, `useGeneratedKeys` inserts and `ESCAPE '\'` LIKE searches all live in one mapper file.
* **JTE Templates:** High-performance, compile-time checked server-side rendering with Java Template Engine.
* **Zero-Build Frontend:** Client-side updates, form submissions, and API interactions handle smoothly via Axios loaded directly from a CDN — no npm install, no bundler, no build step.
* **SQLite Database:** Embedded file-based database (`springdb.db`) — no MySQL/server setup required; schema and data managed by Flyway migrations.
* **SaaS Management:** API keys, webhooks, audit logs, and usage analytics out of the box.
* **RAG AI Chat:** Local LLM-powered chat with your documents — upload text, ask questions, get cited answers. Powered by Ollama (llama3.2 + nomic-embed-text).
* **Gradle Wrapper:** Build and run cleanly out of the box without requiring local Gradle installations.

## 🛠️ Tech Stack
| **Layer** | **Technology** |
|---|---|
| **Backend Framework** | Spring Boot 4.1 |
| **Language** | Kotlin 2.3 |
| **Persistence** | MyBatis (`mybatis-spring-boot-starter` 4.1.0) + HikariCP |
| **Template Engine** | JTE (Java Template Engine) |
| **Database** | SQLite (embedded, file-based — no server required) |
| **Schema Migration** | Flyway |
| **AI/LLM Runtime** | Ollama (local, Docker-native) |
| **Embedding Model** | nomic-embed-text |
| **Chat Model** | llama3.2 (3B params, CPU-friendly) |
| **Build System** | Gradle (Kotlin DSL - `build.gradle.kts`) |
| **Frontend Utilities** | HTML5, Axios 1.20.0 (jsDelivr CDN) |

## 📁 Repository Structure
```text
├── .github/workflows/      # CI/CD Automation workflows
├── gradle/wrapper/         # Gradle Wrapper files
├── src/
│   ├── main/
│   │   ├── kotlin/         # Spring Boot Application code & controllers
│   │   │   └── com/example/springjte/
│   │   │       ├── config/              # MyBatis, DataInitializer, FloatArrayTypeHandler
│   │   │       ├── controller/          # REST & page controllers (inc. RAG)
│   │   │       ├── dto/                 # Request/response DTOs (inc. RAG)
│   │   │       ├── model/               # Entity classes (inc. Document, DocumentChunk, RagConversation)
│   │   │       ├── rag/                 # RAG intelligence layer (NEW)
│   │   │       │   ├── OllamaEmbeddingService.kt
│   │   │       │   ├── SqliteVectorStore.kt
│   │   │       │   ├── DocumentChunker.kt
│   │   │       │   ├── DocumentIngestionService.kt
│   │   │       │   └── RagService.kt
│   │   │       ├── repository/          # Persistence contracts
│   │   │       ├── security/            # Spring Security config
│   │   │       └── service/             # Business logic services
│   │   └── resources/
│   │       ├── mappers/    # MyBatis XML statements
│   │       ├── db/migration/ # Flyway SQL migrations
│   │       ├── templates/  # JTE templates (.jte files)
│   │       │   └── rag/    # RAG chat UI template
│   │       ├── static/     # CSS, JS assets (inc. rag-chat.js)
│   │       └── application.properties
│   └── test/               # Unit & integration tests
├── build.gradle.kts        # Project dependencies and build scripts
├── docker-compose.yml      # App + Ollama services
├── Dockerfile
├── RAG_DESIGN.md           # RAG architecture documentation
└── settings.gradle.kts
```

## 🗄️ Database

The application uses **SQLite** — an embedded, file-based database with zero server setup. The database file (`springdb.db`) is created in the project root automatically on first run and is excluded from version control via `.gitignore`.

### Configuration
```properties
spring.datasource.url=jdbc:sqlite:springdb.db
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.flyway.enabled=true

# MyBatis
mybatis.mapper-locations=classpath:mappers/*.xml
mybatis.type-aliases-package=com.example.springjte.model
mybatis.type-handlers-package=com.example.springjte.config
mybatis.configuration.map-underscore-to-camel-case=true
mybatis.configuration.jdbc-type-for-null=NULL
```

> **Note:** SQLite allows only a single writer at a time, so HikariCP is configured with `maximum-pool-size=1`.

### Schema Migration (Flyway)
Migrations live in `src/main/resources/db/migration/` and run automatically at startup:

| Version | File | Description |
|---|---|---|
| V1 | `V1__create_users.sql` | Creates the `users` table (SQLite dialect) |
| V2 | `V2__import_mysql_users.sql` | Imports existing users from the legacy MySQL database |
| V3 | `V3__create_saas_tables.sql` | API keys, webhooks, audit logs, usage events |
| V4 | `V4__create_books.sql` | Book catalog table with demo data |
| V5 | `V5__rag_documents.sql` | RAG tables: documents, document_chunks, rag_conversations, rag_messages |

V2 preserves the original user IDs, emails, and BCrypt password hashes, so all existing logins continue to work unchanged. A default admin account is seeded on first run by `DataInitializer`:

* **Email:** `admin@example.com`
* **Password:** `admin123`

### Starting Fresh
Delete the database file and restart the app — Flyway will rebuild the schema and re-import the users:
```bash
rm springdb.db
./gradlew bootRun
```

## 🤖 RAG / AI Chat

The application includes a full **Retrieval-Augmented Generation** pipeline that lets users chat with uploaded documents using a local LLM.

### How it works
1. **Upload documents** via `POST /api/rag/documents` — text is chunked, embedded, and stored in SQLite
2. **Ask questions** via the chat UI (`/rag/chat`) or API (`POST /api/rag/chat`)
3. **Retrieve context** — the system finds the most semantically similar document chunks using cosine similarity
4. **Generate answer** — Ollama's LLM answers based only on the retrieved context, with source citations

### Key Components
| Component | File | Role |
|---|---|---|
| Embedding Service | `rag/OllamaEmbeddingService.kt` | HTTP client for Ollama `/api/embeddings` and `/api/generate` |
| Vector Store | `rag/SqliteVectorStore.kt` | Cosine-similarity search over FloatArray embeddings stored as SQLite BLOBs |
| Chunker | `rag/DocumentChunker.kt` | Smart text splitting by paragraph → sentence → character |
| Ingestion | `rag/DocumentIngestionService.kt` | End-to-end: chunk → embed (batch) → persist |
| Orchestrator | `rag/RagService.kt` | Full pipeline: embed question → retrieve top-k → build prompt → generate |
| Chat API | `controller/RagApiController.kt` | REST endpoints + SSE streaming |
| Chat UI | `templates/rag/chat.jte` + `static/rag-chat.js` | Streaming chat interface with conversation history |

### RAG Configuration
```properties
rag.ollama.base-url=${RAG_OLLAMA_BASE_URL:http://localhost:11434}
rag.ollama.model=llama3.2
rag.ollama.embedding-model=nomic-embed-text
rag.ollama.timeout-seconds=60
rag.chunk.size=500
rag.chunk.overlap=50
rag.retrieval.top-k=5
rag.retrieval.similarity-threshold=0.7
```

### RAG API Endpoints
| Endpoint | Method | Description |
|---|---|---|
| `/api/rag/chat` | POST | Blocking chat with full response |
| `/api/rag/chat/stream` | POST | Streaming chat via SSE (token-by-token) |
| `/api/rag/conversations` | GET | List user's conversations |
| `/api/rag/conversations/{id}` | GET | Get conversation history |
| `/api/rag/documents` | POST | Upload a document to the knowledge base |
| `/api/rag/documents` | GET | List accessible documents |
| `/api/rag/documents/{id}` | DELETE | Remove a document |
| `/api/rag/health` | GET | Ollama backend health check |

## 🧩 Persistence with MyBatis

All SQL is hand-written and lives in **XML mappers**. There is no ORM and no `JdbcTemplate` anywhere in the codebase.

```text
UserService ──► UserRepository (interface)
                     ▲
                     │ implements
          MyBatisUserRepository ──► UserMapper (@Mapper interface)
                                          ▲
                                          │ generated by MyBatis from
                                   resources/mappers/UserMapper.xml
```

| Piece | File | Role |
|---|---|---|
| Contract | `repository/UserRepository.kt` | What the service depends on; keeps the SQL layer swappable (tests use an in-memory double) |
| Mapper | `repository/mybatis/UserMapper.kt` | `@Mapper` interface — no implementation; statement ids match method names |
| SQL | `resources/mappers/UserMapper.xml` | Every statement: selects, insert, update, delete |
| Adapter | `repository/mybatis/MyBatisUserRepository.kt` | Turns a search term into an escaped LIKE pattern, returns the generated key |

**Dynamic search.** The `WHERE` clause is a reusable fragment that disappears when nothing is typed:

```xml
<sql id="searchFilter">
    <if test="pattern != null">
        WHERE name LIKE #{pattern} ESCAPE '\'
           OR email LIKE #{pattern} ESCAPE '\'
    </if>
</sql>
```

**Generated keys.** The insert writes the new id straight back into the entity:

```xml
<insert id="insert" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
    INSERT INTO users (name, email, password, role, email_verified)
    VALUES (#{name}, #{email}, #{password}, coalesce(#{role}, 'ROLE_USER'), 0)
</insert>
```

### Adding a query

1. Add the method to `UserMapper` (annotate every parameter with `@Param` — Kotlin does not keep parameter names in the class file).
2. Add a statement with the same `id` in `UserMapper.xml`.
3. Expose it through `UserRepository` + `MyBatisUserRepository` if the service needs it.

### Seeing the SQL

MyBatis logs every statement at `DEBUG` for the mapper package:

```properties
logging.level.com.example.springjte.repository.mybatis=DEBUG
```
```text
==>  Preparing: SELECT id, name, email, password, role FROM users WHERE name LIKE ? ESCAPE '\' ...
<==      Total: 3
```

### Why `User` uses `var`

MyBatis instantiates entities with a no-arg constructor and fills them through setters, and `useGeneratedKeys` writes the new id into `User.id` after an insert. A fully immutable data class would need a `<constructor>` result map plus a mutable holder for the key, so the entity is deliberately mutable.

---

## 🐳 Docker

The project ships with a multi-stage `Dockerfile` (build with Gradle, run on Temurin JRE 25) and a `docker-compose.yml` that includes both the app and an **Ollama** sidecar for AI inference.

### Build & run with Docker Compose (includes Ollama)
```bash
docker compose up --build
```
This starts both services:
* **App:** `http://localhost:8080/` (login at `/login`)
* **Ollama:** `http://localhost:11434/` (LLM inference API)

Ollama will automatically pull `nomic-embed-text` and `llama3.2` on first startup (~2GB download).

### Build & run with plain Docker (app only, no AI)
```bash
docker build -t springsqlite .
docker run -d --name springsqlite -p 8080:8080 -v springsqlite-data:/data springsqlite
```

### Data persistence
The SQLite database is stored at `/data/springdb.db` inside the container and is mounted on the named volume `springsqlite-data`, so data survives container restarts/rebuilds. To reset:
```bash
docker compose down -v   # removes the volume
docker compose up --build
```

### Configuration
Override the database location or any Spring property via environment variables:
```yaml
environment:
  DB_URL: jdbc:sqlite:/data/springdb.db
  RAG_OLLAMA_BASE_URL: http://ollama:11434
```

---

## ⚙️ Getting Started

### Prerequisites
Ensure you have the following installed on your machine:
* **Java Development Kit (JDK):** Version 17 or higher (recommended: 25)
* **IDE:** IntelliJ IDEA (highly recommended for Kotlin and JTE syntax support)
* **Docker:** Required for the RAG/AI chat feature (Ollama runs in a container)

### Running without AI (books + users only)
If you don't need the RAG chat feature, Ollama is optional. The app will start but AI endpoints will report "offline":
```bash
./gradlew bootRun
```

### Running with AI (full stack)
1. Start Ollama in Docker (first run downloads ~2GB of models):
   ```bash
   docker compose up ollama
   ```
2. In another terminal, start the Spring Boot app:
   ```bash
   ./gradlew bootRun
   ```
3. Open the app at `http://localhost:8080`
4. Navigate to **RAG Chat** from the sidebar

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/harrysuke/springjte
   cd springjte
   ```
2. Build the project using the Gradle wrapper:
   ```bash
   ./gradlew build
   ```

## 💡 Development Patterns Used

### 1. Server-Side Rendering (JTE)
Templates are located in `src/main/resources/templates`. JTE compiles these templates directly into Java bytecode, making it incredibly fast.

Example Kotlin Controller integration:
```kotlin
@Controller
class HomeController {
    @GetMapping("/")
    fun index(model: Model): String {
        model.addAttribute("title", "Welcome to SpringJTE")
        return "index" // Renders index.jte
    }
}
```

### 2. Client-Side Interactions (Axios)
Instead of importing a massive JavaScript framework, asynchronous state updates use Axios, pulled from a CDN at runtime:
```javascript
// Example frontend pattern inside the HTML scripts
axios.post('api/users', { name: 'Alice', email: 'alice@example.com', password: 'secret12' })
.then(({ data }) => {
    console.log('Success:', data);
    // Dynamically update DOM here
})
.catch(error => {
    // Axios rejects on any non-2xx status; the response body is on error.response
    console.error('Error:', error.response?.data ?? error.message);
});
```

Axios also handles the boilerplate for you: it serialises the JSON body, sets `Content-Type: application/json`, and reads the non-HttpOnly `XSRF-TOKEN` cookie that Spring Security publishes so it can send the matching `X-XSRF-TOKEN` header on every mutating request.

### 3. RAG Streaming Chat (SSE)
The RAG chat interface streams LLM tokens in real-time using Server-Sent Events:
```javascript
fetch('/api/rag/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: 'What is clean code?' })
})
.then(response => {
    const reader = response.body.getReader();
    // Read chunks and append to the chat UI as they arrive
});
```
