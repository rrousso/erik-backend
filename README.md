# Erik — Narrative Simulation Engine

## Overview

**Erik** is not a story generator.

It is a **narrative simulation**: a space you enter, where an AI narrator embodies a living scene that responds to what you do, not to what you ask for.

During a session (called a *stanza*), the system creates a consistent fictional world with rules, memory, and emotional continuity. The narrator does not prompt you with "what happens next?" — the world waits for you to act inside it.

This makes Erik closer to a **theatrical space** or **roleplay engine** than to a traditional interactive fiction tool.

---

## How to Experience Erik

Erik is not used like a chatbot.

Once a stanza begins, you are inside a living narrative scene. The narrator will describe what exists and what happens — but it will not ask you what to do next.

You interact by **acting inside the world**:
- speak to characters
- move through space
- make decisions
- introduce intentions

The world advances in response to what you do, not to prompts like "continue the story".

---

## Example Interaction

```
[Erik]
"Hey there! Ready to create a new adventure to protagonize?"

> I want to explore an abandoned research facility in the Arctic

[Erik]
"Ooh, nice. What's your role? Are you part of a team, or solo?"

> Solo expedition, I'm a journalist investigating disappearances

[Erik]
"Perfect. Tone-wise, are we going tense thriller or full horror?"

> Tense thriller with creeping dread

[Erik]
"Got it. So: Arctic research facility, you're a solo journalist investigating 
disappearances, tense thriller with creeping dread. Ready to begin?"

> let's do it

[STANZA START]

[Opening Narration]
The helicopter's rotors fade into the distance, swallowed by the Arctic wind...
```

---

## Key Concepts

### Two Modes

**VOID Mode** — Planning Space
- Talk with Erik, your creative assistant
- Plan and shape your narrative
- Discuss tone, setting, characters
- Pause mid-stanza to make adjustments
- Browse and load previous stanzas

**STANZA Mode** — Living Narrative
- The Narrator takes over
- Second-person present tense narration
- You act, the world responds
- Characters react based on what they know
- The scene evolves organically

### Commands

Commands work naturally or can be made explicit with double parentheses:

**System Commands (prefix with `/`):**
- `/help` — Show all available commands
- `/list` — List all saved stanzas from the database
- `/search [keywords]` — Full-text search across all fields
- `/search setting:[keyword]` — Search by setting field
- `/search character:[name]` — Search by character name
- `/load [id]` — Load a stanza into Erik's memory for reference/continuation
- `/clear` — Clear loaded stanza from memory

**In Void Mode:**
- `"let's begin"` / `"start"` → Start the stanza
- Natural conversation with Erik
- `"show persona"` → Display your persona information

**In Stanza Mode:**
- `((pause))` → Pause and return to Erik
- `((end))` → Request narrative closure and end stanza
- `((abandon))` → Drop the stanza without ceremony
- `((add rain to the scene))` → Out-of-character directives to the narrator

**Universal:**
- `exit` → Close the application

### Lifecycle

```
App Start → VOID (planning with Erik)
  ↓ "start"
STANZA (active narration)
  ↓ ((pause))
VOID (discuss adjustments with Erik)
  ↓ "continue"
STANZA (narration resumes with changes)
  ↓ ((end))
VOID (reflection with Erik)
  [Session complete - reflection mode only]
```

Abandoned stanzas allow starting fresh. Completed stanzas are saved to the database and end the creative session.

---

## Stanza Memory & Continuation

### Loading Previous Stanzas

⚠️ **EXPERIMENTAL FEATURE — UNDER TESTING**

You can load a previously completed stanza into Erik's memory for reference or continuation:

```bash
> /list                    # See all saved stanzas
> /search vampire romance  # Find stanzas by keywords
> /load 5                  # Load stanza #5 into Erik's memory
```

Once loaded, Erik can:
- Discuss what happened in that stanza
- Help you plan a sequel or variation
- Start a new stanza that continues the story

When you say "let's begin" with a loaded stanza, the system will:
1. Use the loaded stanza's setup (setting, characters, premise, etc.)
2. Include `previousEvents` from that stanza
3. Apply any changes you discussed with Erik during planning

Use `/clear` to remove the loaded stanza from memory if you want to start fresh.

**Note:** This feature is still being tested. Continuation quality depends on how well the original stanza was documented and may vary.

### Search Capabilities

The search system supports multiple modes:

```bash
/search vampire romance          # Full-text search across all fields
/search setting:castle           # Search only in setting field  
/search premise:investigation    # Search only in premise field
/search tone:horror              # Search only in tone field
/search character:Derek          # Search by character name
```

Full-text search uses PostgreSQL's built-in text search with GIN indexing for fast results.

---

## Technical Architecture

### Dual Model System

Erik uses two models for optimal performance:

**Narrative Model** (Claude Sonnet 4.5)
- Erik (void mode conversations)
- Narrator (stanza mode narration)
- Temperature: 0.6 (creative but focused)
- Max tokens: 3000

**Analytical Model** (Gemini Flash 2.5)
- Flag detection (command parsing)
- Stanza setup extraction
- Quick synopsis generation
- Change distillation during pause
- World snapshot updates
- Temperature: 0.3 (precise)
- Max tokens: 3000

### Service Architecture

The application follows a clean service-oriented architecture:

```
ConsoleRunner (Controller)
    ↓
CommandService ←──────────────────┐ (handles /commands)
    ↓                             │
SessionFlowService (Orchestrator) │
    ├── FlagDetectorService       │
    ├── SessionAssemblerService   │
    ├── SystemPromptBuilderService│
    ├── StanzaExtractorService    │
    ├── SynopsisGeneratorService  │
    └── LLMClientService          │
                                  │
StanzaRecordRepository ───────────┘ (database access)
```

**Key Services:**
- **CommandService**: Handles deterministic `/` commands (no LLM needed)
- **SessionAssemblerService**: Assembles `SessionContext` snapshots for prompts
- **SystemPromptBuilderService**: Uses `PromptComposer` to build system prompts
- **SessionFlowService**: Orchestrates mode switching and flag handling

### Memory Management

**Rolling Synopsis System:**
- Maintains world state across the conversation
- Updates periodically (configurable threshold)
- Compresses old exchanges into structured snapshots
- Keeps recent messages in full context
- Prevents context window bloat

**Configuration:**
- `erik.round-window-size`: Number of recent message exchanges to keep (default: 6)
- `erik.round-threshold-size`: History size that triggers synopsis generation (default: 18)

**World Snapshot Format:**
```
EVENT HISTORY: [chronological major events]
CURRENT STATE - WORLD: [public observable facts]
CURRENT STATE - USER_ONLY: [private user knowledge]
CURRENT STATE - CHARACTERS: [what each character knows/believes]
META: [active rules and OOC conditions]
```

### Information Boundaries

**Public vs Private Knowledge:**
- **User Role** (PUBLIC): What characters can observe about the user
- **User Backstory** (PRIVATE): Secrets, trauma, hidden identity — only the narrator knows
- Characters can NEVER know private backstory unless explicitly revealed in-scene
- Characters are NOT psychic — they only know what they observe or are told

**Conversation Isolation:**
- Void and Stanza have **separate conversation histories**
- Characters only know what they've learned in-scene
- Out-of-character directives `((like this))` modify the scene without being narrated
- Erik personality stays in void mode (synopsis system prevents personality bleed)

### Persistence Layer

**PostgreSQL Database:**
- **Personas**: User identity (name, pronouns, physical description, details)
- **Stanza Records**: Completed stanzas with full setup and synopses
- Automatic timestamps (created_at, updated_at)
- One-to-many relationship: Persona → StanzaRecords
- Full-text search with GIN indexing

**Stanza Record Schema:**
```
- Quick Synopsis (narrative, ~150 words)
- Setting, Premise, User Role (public)
- User Backstory (private)
- Tone/Genre
- Characters Present
- Previous Events (chronological list)
- Special Rules
```

---

## Tech Stack

- **Language:** Java 17
- **Framework:** Spring Boot 3.2.1
- **Database:** PostgreSQL (with JPA/Hibernate)
- **LLM API:** OpenRouter
- **Models:** Claude Sonnet 4.5 (narrative) + Gemini Flash 2.5 (analytical)
- **Interface:** Console (current) — designed to extend to REST API

**Dependencies:**
- Spring Data JPA
- PostgreSQL JDBC Driver
- Spring Boot Web (for future REST API)
- JUnit 5 + Mockito (testing)

---

## Running the Project

### Prerequisites
- Java 17+
- Maven
- PostgreSQL (running locally or accessible remotely)
- OpenRouter API key

### Database Setup

1. **Install PostgreSQL** (if not already installed)
```bash
# macOS
brew install postgresql
brew services start postgresql

# Ubuntu/Debian
sudo apt-get install postgresql postgresql-contrib
sudo service postgresql start

# Windows: Download from postgresql.org
```

2. **Create Database**
```bash
# Connect to PostgreSQL
psql postgres

# Create database and user
CREATE DATABASE erik_db;
CREATE USER your_db_user WITH PASSWORD 'your_db_password';
GRANT ALL PRIVILEGES ON DATABASE erik_db TO your_db_user;
\q
```

3. **Enable Full-Text Search** (optional but recommended)
```sql
-- Run after the application creates tables (first run)
ALTER TABLE stanza_records ADD COLUMN IF NOT EXISTS search_vector tsvector;

CREATE INDEX IF NOT EXISTS idx_stanza_search ON stanza_records USING GIN(search_vector);

CREATE OR REPLACE FUNCTION update_search_vector() RETURNS trigger AS $$
BEGIN
  NEW.search_vector := 
    setweight(to_tsvector('english', coalesce(NEW.setting, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(NEW.premise, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(NEW.tone, '')), 'B') ||
    setweight(to_tsvector('english', coalesce(NEW.quick_synopsis, '')), 'C');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER stanza_search_update BEFORE INSERT OR UPDATE
ON stanza_records FOR EACH ROW EXECUTE FUNCTION update_search_vector();

-- Update existing records
UPDATE stanza_records SET setting = setting;
```

4. **Set Environment Variables**
```bash
export DB_USER=your_db_user
export DB_PASS=your_db_password
export OPENROUTER_API_KEY=your_openrouter_key
```

### Application Setup

1. **Clone the repository**
```bash
git clone <repo-url>
cd erik-core
```

2. **Configure database connection** (optional — defaults to localhost)

Edit `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/erik_db
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASS}
```

3. **Run the application**
```bash
mvn clean spring-boot:run
```

4. **First Run Setup**
- Application will prompt you to create a persona
- Enter: name, pronouns, physical description, other details
- Persona is saved to PostgreSQL database
- Future runs will load this persona automatically

### Configuration

Edit `src/main/resources/application.properties`:

```properties
# Spring Boot Configuration
spring.application.name=erik-core
spring.main.web-application-type=none
spring.main.banner-mode=off

# JPA/Hibernate Settings
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Erik - Narrative Model (Claude Sonnet 4.5)
erik.narrative.model=anthropic/claude-sonnet-4.5
erik.narrative.temperature=0.6
erik.narrative.max-tokens=3000

# Erik - Analytical Model (Gemini Flash 2.5)
erik.analytical.model=google/gemini-2.5-flash
erik.analytical.temperature=0.3
erik.analytical.max-tokens=3000

# Erik - Context Window Settings
erik.round-window-size=6        # Recent messages to keep
erik.round-threshold-size=18    # When to generate synopsis

# Logging
logging.level.root=WARN
logging.level.com.github.rrousso.erik_core=INFO
```

### Console Commands

Once running:
```
> I want to do a Cinderella story          # Planning conversation
> let's begin                              # Start stanza
> I hesitate                               # In-stanza action
> ((pause))                                # Pause and return to Erik
> continue                                 # Resume stanza
> ((end))                                  # End stanza with closure
> show persona                             # Display your persona
> /list                                    # View saved stanzas
> /search vampire                          # Search stanzas
> /load 5                                  # Load stanza #5 for continuation
> /clear                                   # Clear loaded stanza
> /help                                    # Show all commands
> exit                                     # Close application
```

---

## Project Structure

```
erik-core/
├── src/main/java/com/github/rrousso/erik_core/
│   ├── config/
│   │   └── ErikProperties.java           # Configuration properties
│   ├── controllers/
│   │   └── ConsoleRunner.java            # Main console interface
│   ├── entities/
│   │   ├── CommandResult.java            # Command execution result
│   │   ├── CompletedStanza.java          # Completed stanza data
│   │   ├── ConversationHistory.java      # Rolling message history
│   │   ├── Flag.java                     # System command flags
│   │   ├── ModelType.java                # Narrative vs Analytical
│   │   ├── Persona.java                  # User persona entity
│   │   ├── SessionContext.java           # Immutable context snapshot
│   │   ├── SessionState.java             # Current session state
│   │   ├── StanzaMetadata.java           # Stanza configuration
│   │   ├── StanzaRecord.java             # Persisted stanza entity
│   │   └── StanzaStatus.java             # Lifecycle states
│   ├── repositories/
│   │   ├── PersonaRepository.java        # JPA persona repository
│   │   └── StanzaRecordRepository.java   # JPA stanza repository (with search)
│   ├── services/
│   │   ├── CommandService.java           # Handles /commands
│   │   ├── ConfigService.java            # Config & persona management
│   │   ├── FlagDetectorService.java      # Command detection
│   │   ├── LLMClientService.java         # OpenRouter API client
│   │   ├── PromptComposer.java           # Fluent prompt builder
│   │   ├── PromptLoaderService.java      # Load prompt templates
│   │   ├── SessionAssemblerService.java  # Assembles SessionContext
│   │   ├── SessionFlowService.java       # Main orchestration
│   │   ├── StanzaExtractorService.java   # Extract setup from conversation
│   │   ├── SynopsisGeneratorService.java # Generate synopses
│   │   └── SystemPromptBuilderService.java # Build system prompts
│   └── ErikCoreApplication.java          # Spring Boot entry point
├── src/main/resources/
│   ├── prompts/
│   │   ├── analytical/                   # Analytical model prompts
│   │   │   ├── changes_distiller.txt
│   │   │   ├── flag_detection.txt
│   │   │   ├── quick_synopsis.txt
│   │   │   └── world_snapshot_synopsis.txt
│   │   ├── erik/                         # Erik (void mode) prompts
│   │   │   ├── directive_abandoned.txt
│   │   │   ├── directive_completed.txt
│   │   │   ├── directive_paused.txt
│   │   │   ├── directive_planning.txt
│   │   │   └── personality.txt
│   │   ├── narrator/                     # Narrator (stanza mode) prompts
│   │   │   ├── detailed_synopsis.txt
│   │   │   ├── extraction_prompt.txt
│   │   │   └── stanza_narrator.txt
│   │   └── user/
│   │       └── fictional_frame.txt       # Fictional framing
│   └── application.properties            # Spring configuration
├── src/test/java/                        # Unit tests
├── user_data/                            # Runtime generated files
│   ├── extracted_stanza.txt              # Debug output
│   ├── quick_synopsis.txt                # Debug output
│   └── rolling_synopsis.txt              # Debug output
├── pom.xml                               # Maven dependencies
└── README.md                             # This file
```

---

## Project Goals

This project exists to explore:

1. **Narrative Design:** How to create emergent, reactive story spaces
2. **State Management:** Clean session handling, mode switching, lifecycle control
3. **Context Efficiency:** Rolling synopsis, dual-model routing, memory compression
4. **Backend Architecture:** Spring Boot services, separation of concerns, extensibility
5. **Persistence:** JPA/Hibernate for user identity and narrative history

From a **creative perspective**, Erik explores a different model of human–AI storytelling: treating the AI not as a writer that outputs plot, but as a **world that reacts**.

From a **technical perspective**, it's a serious backend experiment in stateful session design, information boundaries, context management, and persistent narrative storage.

---

## Current Status

✅ **Phase 1 Complete** — Core Console Foundation
- Void/Stanza mode switching
- Dual model system (narrative + analytical)
- Rolling synopsis with world snapshots
- Flag detection and command parsing
- Pause/continue with change distillation
- Out-of-character directives
- Session lifecycle management
- PostgreSQL persistence (personas + stanza records)
- Public/private knowledge separation

✅ **Phase 1.5 Complete** — Stanza Management
- `/list`, `/search`, `/load`, `/clear` commands
- Full-text search with PostgreSQL GIN indexing
- Field-specific search (setting, premise, tone, character)
- CommandService for deterministic operations
- SessionAssemblerService and SessionContext pattern
- PromptComposer utility class
- CommandServiceTest with full coverage

🔨 **In Progress: Stanza Injection** (TESTING)
- Load previous stanzas for continuation
- Extract previousEvents from completed stanzas
- Apply user modifications during planning
- Convert StanzaRecord → StanzaMetadata for re-entry
- **Status:** Feature works but needs more testing

📋 **Next: Phase 2** — Enhanced Knowledge & Retrieval
- Character-specific memory extraction
- Relationship tracking across stanzas
- Improved context injection per character
- Multi-persona support

📋 **Future: Phase 3** — REST API & Web Interface
- Spring Boot REST API
- React frontend
- Multi-user sessions
- Real-time collaboration

---

## Database Schema

### Personas Table
```sql
CREATE TABLE personas (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    pronouns VARCHAR(255),
    description VARCHAR(1000),
    other_details VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Stanza Records Table
```sql
CREATE TABLE stanza_records (
    id BIGSERIAL PRIMARY KEY,
    persona_id BIGINT NOT NULL REFERENCES personas(id),
    quick_synopsis VARCHAR(2000),
    setting VARCHAR(500),
    premise VARCHAR(1000),
    user_role VARCHAR(500),
    user_backstory VARCHAR(500),
    tone VARCHAR(200),
    search_vector tsvector,  -- For full-text search
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE stanza_characters (
    stanza_id BIGINT NOT NULL REFERENCES stanza_records(id),
    character_name VARCHAR(255)
);

CREATE TABLE stanza_events (
    stanza_id BIGINT NOT NULL REFERENCES stanza_records(id),
    event VARCHAR(500)
);

CREATE TABLE stanza_rules (
    stanza_id BIGINT NOT NULL REFERENCES stanza_records(id),
    rule VARCHAR(500)
);

-- Full-text search index
CREATE INDEX idx_stanza_search ON stanza_records USING GIN(search_vector);
```

---

## Testing

The project includes comprehensive unit tests:

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=SessionFlowServiceTest

# Run tests with coverage
mvn clean test jacoco:report
```

**Test Coverage:**
- `ConversationHistoryTest`: Rolling synopsis and message management
- `StanzaSetupTest`: Stanza configuration parsing
- `FlagDetectorServiceTest`: Command detection logic
- `SessionFlowServiceTest`: Core flow orchestration
- `StanzaExtractorServiceTest`: Setup extraction from conversations
- `SynopsisGeneratorServiceTest`: Synopsis generation
- `CommandServiceTest`: `/` command processing

---

## Why This Project Exists

Erik exists to explore a different model of human–AI storytelling:

Instead of treating the AI as a writer that outputs plot,  
Erik treats the AI as a **world that reacts**.

The user is not directing a story.  
They are **present inside a scene**.

This project is designed as both:
- a creative narrative system
- and a clean, extensible Spring Boot architecture

---

## Author

**Rafael Rousso**  
Backend / Java Developer  
Buenos Aires, Argentina

---

## Notes for Developers

This project prioritizes **clarity over completeness**. The architecture is intentionally modular to support future extensions (REST API, persistence, multi-user, React frontend).

**Key Design Principles:**
- Strong separation between modes (void/stanza)
- Separate conversation histories prevent information bleed
- Dual model routing optimizes cost and quality
- Prompts are external files for easy iteration
- Services are Spring-managed for testability
- State is explicit and centralized
- Public/private knowledge boundaries enforced
- Database persistence for long-term memory
- Commands separated from LLM-driven conversation

**Important Implementation Details:**

1. **Synopsis System**: Uses `ConversationHistory` with rolling window
   - Keeps recent messages for immediate context
   - Generates synopsis when threshold exceeded
   - Old messages condensed, recent messages preserved
   - Separate histories for void and stanza modes

2. **Knowledge Separation**: StanzaMetadata has two fields
   - `userRole` (PUBLIC): What characters can observe
   - `userBackstory` (PRIVATE): Narrator-only secrets
   - Extraction prompt enforces this distinction

3. **Flag Detection**: Pre-filters input using analytical model
   - Detects commands before narrative models called
   - Uses conversation context for disambiguation
   - Validates flags against current status

4. **Persistence**: JPA/Hibernate with PostgreSQL
   - Automatic schema generation (ddl-auto=update)
   - Timestamped entities (created_at, updated_at)
   - Element collections for lists (characters, rules, events)
   - Full-text search with GIN indexing

5. **Command System**: Deterministic operations bypass LLM
   - `/` prefix triggers CommandService
   - Results return immediately (no API calls)
   - Commands can modify SessionState (e.g., `/load`)

6. **Context Assembly**: SessionAssemblerService + SessionContext
   - Immutable snapshots for each LLM call
   - Builder pattern for clean construction
   - Separates "what do we know" from "how do we render it"

7. **Prompt Composition**: PromptComposer utility
   - Fluent API for building prompts
   - Conditional sections with `.sectionIf()`
   - Automatic dividers and labeling

---

## License

This project is currently unlicensed and for portfolio/educational purposes.

---

## Acknowledgments

This project explores narrative AI through the lens of:
- Interactive fiction traditions
- Tabletop roleplaying game design
- Backend service architecture
- Context window optimization techniques