# Development Guide

## Prerequisites

- **Java 21** (required)
- **Maven 3.6+**
- **PostgreSQL 14+** with PgVector extension (production only)
- **DashScope API key**

## Setup

### 1. Clone and Configure

```bash
git clone https://github.com/your-username/ron-ai-agent.git
cd ron-ai-agent

# Copy dev config template
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml
```

Edit `application-dev.yml` with your credentials.

### 2. Set Environment Variables

```bash
export DASHSCOPE_API_KEY=your-api-key
export DATABASE_URL=jdbc:postgresql://localhost:5432/ron_ai_agent
export DATABASE_USERNAME=your-username
export DATABASE_PASSWORD=your-password
export SEARCH_API_KEY=your-search-api-key
```

### 3. Build and Run

```bash
mvn clean compile          # Build
mvn test                   # Run tests (H2, no PostgreSQL needed)
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # Run with dev profile
```

## Build Commands

<!-- AUTO-GENERATED:START -->
| Command | Description |
|---------|-------------|
| `mvn clean compile` | Compile source code |
| `mvn test` | Run all tests (194 tests, H2 in-memory DB) |
| `mvn test -Dtest=RonManusTest` | Run single test class |
| `mvn test -Dtest=RonManusTest#testMethod` | Run single test method |
| `mvn spring-boot:run -Dspring-boot.run.profiles=dev` | Run with dev profile |
| `mvn clean package` | Build JAR |
| `mvn clean package -DskipTests` | Build JAR without tests |
<!-- AUTO-GENERATED:END -->

## Project Structure

```
ron-ai-agent/
├── src/main/java/com/ron/ronaiagent/    # Source code
├── src/main/resources/                   # Config, schema, templates
├── src/test/java/com/ron/ronaiagent/     # Tests
├── src/test/resources/                   # Test config
├── docs/                                 # Documentation
├── ron-image-search-mcp-server/          # MCP server module
├── CLAUDE.md                             # AI assistant instructions
├── pom.xml                               # Maven config
└── README.md
```

## Testing

### Test Stack
- **JUnit 5** — Test framework
- **Mockito** — Mocking (via Spring Boot test)
- **H2** — In-memory database for tests
- **Spring Boot Test** — Context testing

### Test Conventions

- Tests use `@ActiveProfiles("test")` with H2
- No external services needed — DashScope is mocked where required
- Integration tests use `@SpringBootTest`
- Coverage target: 80%+

### Writing Tests

Follow TDD workflow:
1. Write failing test (RED)
2. Write minimal implementation (GREEN)
3. Refactor (IMPROVE)

```java
@SpringBootTest
@ActiveProfiles("test")
class MyComponentTest {
    @Resource
    private MyComponent component;

    @Test
    void shouldDoSomething() {
        // arrange
        // act
        String result = component.doSomething("input");
        // assert
        assertNotNull(result);
    }
}
```

## Coding Standards

- **Lombok** used throughout (`@Data`, `@Slf4j`, `@Builder`)
- **Spring AI** `ToolCallback` interface for all tools
- **Conventional commits**: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`
- **No hardcoded secrets** — use environment variables
- **English for new code comments** — mixed Chinese/English exists in older code
- **Immutability** — use records and `List.copyOf()`

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Write tests first (TDD)
4. Implement with minimum necessary code
5. Run `mvn test` — all tests must pass
6. Commit with conventional commit format
7. Push and open a Pull Request
