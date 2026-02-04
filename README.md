# Erik - AI-Driven Creative Narrative System

> ⚠️ **IMPORTANT: UNSTABLE VERSION - NOT FULLY TESTED**
> 
> This application is currently in active development and **NOT production-ready**.
> - Core features are working but may contain bugs
> - Test coverage is incomplete
> - Database migrations may change
> - API behavior may change without notice
> - Not all edge cases have been tested
> 
> **Use for development and experimentation only.**  
> See [Testing](#testing) section for current test coverage.

---

## Table of Contents

- [Overview](#overview)
- [How It Works](#how-it-works)
- [System Architecture](#system-architecture)
- [Stanza Lifecycle](#stanza-lifecycle)
- [Database Design](#database-design)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Testing](#testing)
- [Development Status](#development-status)
- [Known Issues](#known-issues)

---

## Overview

**Erik** is a Java Spring Boot application that provides an interactive storytelling environment powered by a dual-LLM architecture. It maintains strict separation between planning conversations (VOID mode with Erik) and active storytelling (STANZA mode with the Narrator), while tracking persistent narrative state in a PostgreSQL database.

### Core Features

✅ **Dual Mode System**
- **VOID MODE**: Plan and discuss stories with Erik (your creative collaborator)
- **STANZA MODE**: Experience stories with the Narrator (your story guide)
- Complete separation prevents personality/knowledge bleeding between modes

✅ **Information Boundaries**
- Characters only know what they've directly observed or been told
- User's private backstory is hidden from characters
- Separate conversation histories prevent context leaking

✅ **Persistent World State** (Phase 1 & 2 Complete)
- PostgreSQL database tracks everything in real-time
- Characters, secrets, tensions, events, and knowledge states
- Mid-stanza extraction updates world state during narration
- Load and continue previous stanzas

✅ **Strategy Pattern Architecture**
- Clean, maintainable codebase after major refactoring
- Each operation isolated in its own strategy class
- Easy to extend without modifying existing code

---

## How It Works

### The Dual-LLM Architecture

Erik uses **two different AI models** for different purposes:

**Claude Sonnet 4.5** (Creative Narrative)
- Powers both Erik (planner) and the Narrator (storyteller)
- Temperature: 0.6 (creative but consistent)
- Used for all narrative content and planning discussions

**Gemini Flash 2.5** (Analytical Tasks)
- Detects user commands (START, PAUSE, END, etc.)
- Extracts narrative state changes from story exchanges
- Analyzes world state for prompt building
- Temperature: 0.3 (precise and consistent)

This separation optimizes both quality and API costs.

### Two-Mode System

#### VOID MODE (Planning with Erik)

```
User: "I want to explore a Teen Wolf scenario"
Erik: "Interesting! Who should be present in the stanza?"
User: "Scott, Stiles, Derek, and maybe Allison"
Erik: "What tensions or secrets should we track?"
User: "Scott's trying to keep his werewolf identity hidden from Allison"
Erik: "Perfect! Ready to begin?"
```

**Characteristics:**
- Pure planning and creative discussion
- No narrative action happens
- Separate conversation history (prevents bleed into stanza)
- Erik helps you set up the world, characters, tensions, and secrets

#### STANZA MODE (Active Storytelling)

```
Narrator: "You find yourself in the school hallway after hours..."
User: "I listen carefully for any sounds"
Narrator: "Your enhanced werewolf hearing picks up footsteps..."
```

**Characteristics:**
- The Narrator controls the fictional world
- You experience the story as your character
- Every exchange is analyzed and tracked in the database
- Information boundaries enforced (characters only know what they should)
- Rolling synopsis system keeps context manageable

### The Information Boundary System

Erik maintains strict kayfabe through multiple layers:

**1. Character Knowledge Tracking**
- Database tracks what each character knows via `CharacterKnowledge` entities
- Three awareness states: UNAWARE → SUSPICIOUS → KNOWS
- Characters can only act on information they actually know

**2. User Private Backstory**
- Stored in `StanzaCharacter` when `isUser=true`
- Only visible to the Narrator (not to NPCs)
- Allows for hidden motivations and secret identities

**3. Secret System**
- Secrets tracked in `Secret` entities with discovery rules
- `CharacterSecretState` tracks each character's awareness
- Extraction system updates when secrets are revealed/suspected

**4. Separate Histories**
- VOID mode has its own conversation history
- STANZA mode has its own conversation history
- Prevents Erik's personality from bleeding into Narrator
- Prevents planning discussions from affecting character knowledge

### The Extraction System (Phase 2)

After each narrative exchange, the **StanzaExtractionService** analyzes what happened:

```
User: "I tell Derek that Scott is a werewolf"
Narrator: "Derek's eyes widen in surprise..."

→ Extraction Process:
   1. Build prompt with current database state
   2. Gemini analyzes the exchange
   3. Detects: Knowledge transfer occurred
   4. Creates: CharacterKnowledge (Derek → "Scott is werewolf")
   5. Creates: StanzaEvent ("User revealed Scott's secret to Derek")
   6. Updates: Any relevant tensions
```

**What Gets Extracted:**

| Type | Example | Database Impact |
|------|---------|-----------------|
| **Events** | "User found ancient scroll" | New `StanzaEvent` entry |
| **Knowledge Transfers** | "Derek learned user is a prince" | New `CharacterKnowledge` + `Fact` |
| **Secret Revelations** | "Stiles suspects user's identity" | Update `CharacterSecretState` |
| **Tension Changes** | "Identity crisis escalated" | Update `Tension` pressure |
| **Character Appearances** | "Allison entered the room" | Update `StanzaCharacter` presence |

**Configurable Frequency:**
```properties
# Extract every exchange (expensive but accurate)
erik.extraction.frequency=1

# Extract every 3rd exchange (67% cost savings)
erik.extraction.frequency=3

# Always extract opening and closing narration
erik.extraction.always-extract-on-start=true
erik.extraction.always-extract-on-end=true
```

### The Synopsis System

As conversations grow, Erik compresses older exchanges into summaries:

**Configuration:**
```properties
erik.round-window-size=6        # Keep last 6 exchanges in full
erik.round-threshold-size=18    # Generate synopsis after 18 exchanges
```

**How It Works:**
1. First 6 exchanges: Sent in full to the LLM
2. After 18 exchanges: Older content compressed into synopsis
3. Synopsis updated periodically as conversation continues
4. Most recent exchanges always kept in full detail

This keeps context windows manageable while preserving narrative coherence.

---

## System Architecture

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────┐
│                      ConsoleRunner                          │
│                 (User Interface Layer)                      │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                  SessionFlowService                         │
│              (Main Orchestrator - 50 lines)                 │
│                                                             │
│  1. Detect flags (START/PAUSE/END/etc)                     │
│  2. Select appropriate strategy                            │
│  3. Execute and return response                            │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ├──→ FlagDetectorService (Gemini)
                 │
                 └──→ FlowStrategyFactory
                         │
                         ├──→ VoidModeStrategy
                         ├──→ StanzaModeStrategy  
                         ├──→ StartStanzaStrategy ───→ StanzaInitializationService
                         ├──→ PauseStanzaStrategy
                         ├──→ ContinueStanzaStrategy
                         ├──→ EndStanzaStrategy
                         └──→ AbandonStanzaStrategy
```

### Service Layer Structure

**Orchestration Services:**
```
services/orchestration/
├── SessionFlowService.java          # Main entry point (50 lines)
├── ConversationService.java         # Unified LLM conversation handler
├── StanzaCompletionService.java     # Shared stanza completion logic
└── strategies/
    ├── FlowStrategy.java            # Interface all strategies implement
    ├── FlowStrategyFactory.java     # Selects correct strategy
    ├── VoidModeStrategy.java        # Regular Erik conversation
    ├── StanzaModeStrategy.java      # Regular Narrator narration
    ├── StartStanzaStrategy.java     # Handle START flag
    ├── PauseStanzaStrategy.java     # Handle PAUSE flag
    ├── ContinueStanzaStrategy.java  # Handle CONTINUE flag
    ├── EndStanzaStrategy.java       # Handle END flag
    └── AbandonStanzaStrategy.java   # Handle ABANDON flag
```

**State Extraction Services:**
```
services/stanza/
├── StanzaExtractionService.java     # Main orchestrator (100 lines)
├── StanzaInitializationService.java # Phase 1: Extract initial setup
└── appliers/
    ├── ExtractionApplier.java              # Generic interface
    ├── ExtractionApplierRegistry.java      # Type-safe registry
    ├── EventApplier.java                   # Apply events to DB
    ├── KnowledgeTransferApplier.java       # Apply knowledge changes
    ├── SecretRevelationApplier.java        # Apply secret awareness
    ├── TensionChangeApplier.java           # Apply tension updates
    └── CharacterAppearanceApplier.java     # Apply presence changes
```

**Before vs After Refactoring:**

| Component | Before | After | Improvement |
|-----------|--------|-------|-------------|
| SessionFlowService | 500+ lines | 50 lines | 90% reduction |
| StanzaExtractionService | 600+ lines | 100 lines | 85% reduction |
| Code Duplication | High | Zero | Clean delegation |
| Testability | Difficult | Easy | Isolated strategies |

### Core Components

**1. FlagDetectorService** (Gemini)
- Analyzes user input for commands
- Uses conversation context to distinguish descriptive from imperative language
- Example: "I want to start at the beach" (descriptive) vs "Let's start" (command)
- Returns: START_STANZA, PAUSE_STANZA, CONTINUE_STANZA, END_STANZA, ABANDON_STANZA, or NONE

**2. ConversationService**
- Unified interface for LLM conversations
- Handles both Erik (void) and Narrator (stanza) conversations
- Manages context window, synopsis, and conversation history
- Calls appropriate prompt builders based on mode

**3. StanzaExtractionService**
- Analyzes narrative exchanges for state changes
- Calls Gemini with current database state
- Parses JSON response into structured extraction data
- Delegates to specialized appliers for database updates

**4. StanzaPersistenceService**
- Handles all database operations for stanzas
- CRUD operations for stanzas and related entities
- Loads stanzas with all relationships for continuation

**5. Prompt Builders**
- `ErikSystemPromptBuilder`: Builds Erik's planning persona
- `NarratorSystemPromptBuilder`: Builds Narrator's storytelling persona
- `ExtractionPromptBuilder`: Builds analytical extraction prompts
- Each prompt builder pulls current state from database

---

## Stanza Lifecycle

### Complete Lifecycle Example

#### Phase 1: Planning (VOID Mode)

```
User: "I want to do a male Cinderella story at the ball"
Erik: "Interesting! Who should be present?"
User: "The prince, my stepsisters Drizella and Anastasia, and the fairy godmother"
Erik: "What secrets should we track?"
User: "I'm actually a prince from another kingdom in disguise"
Erik: "Any other tensions?"
User: "The stepsisters are suspicious of me, and the kingdom is at war"
Erik: "Perfect setup! Ready to begin?"
User: "Let's start"
```

#### Phase 2: Initialization (START flag detected)

```
→ FlagDetectorService detects START_STANZA
→ StartStanzaStrategy executes:
   1. Calls StanzaInitializationService
   2. Gemini extracts structured data from planning conversation
   3. Creates database entities:
      
      Stanza:
        - title: "Male Cinderella at the Ball"
        - status: "active"
        - currentBeat: 1
      
      Characters:
        - User (isUser=true)
          - publicRole: "Mysterious gentleman at ball"
          - privateBackstory: "Actually a prince from neighboring kingdom"
        - Prince (NPC)
        - Drizella (NPC)
        - Anastasia (NPC)
        - Fairy Godmother (NPC, presenceStatus="potential")
      
      Secrets:
        - "User's true identity as foreign prince"
          - restrictedDiscovery: true
          - allowedRevealModes: ["OBSERVED", "TOLD"]
      
      Tensions:
        - "Will user's identity be discovered?"
          - pressure: 6
          - involvedCharacters: "User, Prince, Stepsisters"
        - "Kingdom at war with user's homeland"
          - pressure: 5

   4. Narrator provides opening narration
   5. Mode switches to STANZA
```

#### Phase 3: Active Narration (STANZA Mode)

```
Narrator: "The ballroom glitters with candlelight as you descend
          the grand staircase. The prince's eyes meet yours across
          the room, and your stepsisters whisper nearby..."

User: "I approach the prince nervously"

→ StanzaModeStrategy executes:
   1. Calls Narrator with current database state
   2. Narrator responds in-character
   3. StanzaExtractionService analyzes exchange:
   
   Extraction Results:
     Events:
       - "User approached the prince at the ball"
     
     CharacterAppearances:
       - Prince: PRESENT (was potential, now actively engaged)
       - Drizella: PRESENT (observing user)
       - Anastasia: PRESENT (observing user)
     
     TensionChanges:
       - "Will user's identity be discovered?"
         - pressure: 6 → 7 (escalated due to proximity)
     
   4. Database updated with all changes
   5. Exchange counter: 1

Narrator: "The prince extends his hand with a warm smile. 'I don't
          believe we've been introduced,' he says. From the corner
          of your eye, you notice Drizella nudging Anastasia, both
          studying you intently."

User: "I bow and introduce myself as 'Elliot from the countryside'"

→ Extraction continues:
     Events:
       - "User introduced self as 'Elliot from the countryside'"
     
     KnowledgeTransfers:
       - Prince learned: "User's name is Elliot"
       - Drizella learned: "User claims to be from countryside"
       - Anastasia learned: "User claims to be from countryside"
     
     TensionChanges:
       - "Will user's identity be discovered?"
         - pressure: 7 → 6 (de-escalated slightly)
       - "Stepsisters' suspicion of user"
         - changeType: "CREATED"
         - pressure: 4 (new tension emerged)
```

#### Phase 4: Pause & Modification

```
User: "((pause, I want the fairy godmother to appear))"

→ FlagDetectorService detects PAUSE_STANZA
→ PauseStanzaStrategy executes:
   1. Switches to VOID mode
   2. Updates stanza status: "paused"
   3. Erik responds

Erik: "Got it! Want me to bring in the fairy godmother when we
      continue? Should she reveal anything or just observe?"

User: "Have her subtly hint that she knows who I really am, but
      don't let the others hear"

Erik: "Perfect, I'll make sure the Narrator includes that when you
      continue. Ready to go back in?"

User: "Yeah, let's continue"

→ FlagDetectorService detects CONTINUE_STANZA
→ ContinueStanzaStrategy executes:
   1. Switches back to STANZA mode
   2. Updates stanza status: "active"
   3. Narrator incorporates requested changes
   4. Extraction continues

Narrator: "As you and the prince begin to dance, a shimmer of light
          catches your attention. The fairy godmother appears at the
          edge of the ballroom, catching your eye with a knowing smile.
          She mouths something you can't quite make out, but her
          expression suggests she sees right through your disguise..."
```

#### Phase 5: Completion

```
User: "((end stanza))"

→ FlagDetectorService detects END_STANZA
→ EndStanzaStrategy executes:
   1. Narrator provides closing narration
   2. Final extraction (if configured)
   3. Quick synopsis generated and stored
   4. Status updated: "completed"
   5. Switches to VOID mode

Narrator: "As midnight approaches and the spell begins to fade, you
          slip away from the prince's embrace. The fairy godmother
          winks at you as you rush down the stairs, leaving behind
          a single glass slipper..."

Erik: "That was a beautiful scene! The tension between maintaining
      your disguise and growing closer to the prince really came
      through. Want to reflect on what happened?"
```

### Stanza Status State Machine

```
     START
       ↓
    [ACTIVE] ←──────────────┐
       │                    │
       │ PAUSE        CONTINUE
       ↓                    │
    [PAUSED] ───────────────┘
       
    [ACTIVE]
       │
       ├─→ END → [COMPLETED] (immutable)
       │
       └─→ ABANDON → [ABANDONED]
```

**Valid Transitions:**
- `NONE → ACTIVE`: User confirms start with Erik
- `ACTIVE → PAUSED`: User requests pause
- `PAUSED → ACTIVE`: User requests continuation
- `ACTIVE → COMPLETED`: User requests end
- `ACTIVE → ABANDONED`: User requests abandonment

**Invalid Transitions** (prevented by validation):
- Can't pause when already in void mode
- Can't continue when not paused
- Can't end when not active
- Can't restart completed/abandoned stanzas

---

## Database Design

### Core Entities

**Stanza** (Main container)
```
Columns:
- id, title, synopsis, description
- status (none, active, paused, completed, abandoned)
- current_beat (tracks scene progression)
- exchange_count (total exchanges in this stanza)
- created_at, updated_at

Relationships:
- Has many Characters
- Has many Secrets
- Has many Tensions
- Has many Events
- Has many Facts
```

**StanzaCharacter** (People in the story)
```
User Character (isUser=true):
- name, public_role, private_backstory
- visible_traits
- NO knowledge restrictions

NPC Character (isUser=false):
- name, canon_role (if from existing IP)
- emotional_state, motivations, goals
- relationship_to_user
- presence_status (present, potential, background)
- current_location

Relationships:
- Belongs to Stanza
- Has many CharacterKnowledge (what they know)
- Has many CharacterSecretStates (secret awareness)
```

**Secret** (Hidden information)
```
Columns:
- description
- category (identity, ability, past, relationship, etc.)
- restricted_discovery (true/false)
- allowed_reveal_modes (TOLD, OBSERVED, DOCUMENTED, etc.)
- source (USER_BACKSTORY, CHARACTER_DYNAMIC, etc.)

Relationships:
- Belongs to Stanza
- Has many CharacterSecretStates
```

**CharacterSecretState** (Who knows what secrets)
```
Columns:
- awareness_state (UNAWARE, SUSPICIOUS, KNOWS)
- how_learned (TOLD, OBSERVED, INFERRED, etc.)
- context (brief description)

Relationships:
- Belongs to Character
- Belongs to Secret
```

**Tension** (Narrative threads)
```
Columns:
- description
- involved_characters (comma-separated names)
- pressure (1-10 scale)
- potential_triggers
- status (ACTIVE, RESOLVED, DORMANT)
- source, created_beat, last_updated_beat

Relationships:
- Belongs to Stanza
```

**CharacterKnowledge** (Information tracking)
```
Columns:
- awareness_state (KNOWS, SUSPICIOUS, UNAWARE)
- how (TOLD, OBSERVED, DOCUMENTED, INFERRED, SENSED_SPECIAL)
- status (LEARNED, BELIEVED)
- learned_beat, last_referenced_beat

Relationships:
- Belongs to Character
- References Fact
```

**Fact** (Discrete pieces of information)
```
Columns:
- description
- fact_value (true/false - allows tracking misinformation)
- category
- restricted_discovery, allowed_reveal_modes
- source

Relationships:
- Belongs to Stanza
- Has many CharacterKnowledge entries
```

**StanzaEvent** (Narrative log)
```
Columns:
- description (max 280 chars)
- event_type (ACTION, DIALOGUE, REVELATION, STATE_CHANGE, etc.)
- beat_number
- exchange_number
- significance (MAJOR, MODERATE, MINOR)

Relationships:
- Belongs to Stanza
```

### Database Workflow

**During Planning (VOID mode):**
- User discusses with Erik
- No database writes yet
- Planning conversation stored in `SessionState` memory

**On START:**
- `StanzaInitializationService` extracts structured data
- Creates `Stanza` with status="active"
- Creates all `StanzaCharacter` entities
- Creates `Secret` and initial `CharacterSecretState` entries
- Creates `Tension` entities
- Database fully initialized before first narration

**During Narration (STANZA mode):**
- Each exchange analyzed by `StanzaExtractionService`
- New `StanzaEvent` entries created
- `CharacterKnowledge` and `Fact` entries created/updated
- `CharacterSecretState` awareness updated
- `Tension` pressure modified
- `StanzaCharacter` presence status updated
- All changes persisted immediately

**On PAUSE:**
- Status updated to "paused"
- No extraction occurs during pause
- Planning conversation with Erik

**On CONTINUE:**
- Status updated to "active"
- Extraction resumes
- Narrator incorporates any changes discussed during pause

**On END:**
- Final extraction (if configured)
- Quick synopsis generated
- Status updated to "completed"
- Stanza becomes immutable

---

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| **Language** | Java | 17 |
| **Framework** | Spring Boot | 3.2.1 |
| **Database** | PostgreSQL | Latest |
| **ORM** | Hibernate/JPA | (via Spring Boot) |
| **Build Tool** | Maven | Latest |
| **LLM API** | OpenRouter | Latest |
| **Narrative Model** | Claude Sonnet 4.5 | anthropic/claude-sonnet-4.5 |
| **Analytical Model** | Gemini Flash 2.5 | google/gemini-2.5-flash |
| **Testing** | JUnit 5 + Mockito | (via Spring Boot) |
| **HTTP Client** | Java HttpClient | (JDK built-in) |
| **JSON Parsing** | Jackson | (via Spring Boot) |

---

## Getting Started

### Prerequisites

- **Java 17 or higher** ([Download](https://adoptium.net/))
- **Maven** ([Download](https://maven.apache.org/download.cgi))
- **PostgreSQL** ([Download](https://www.postgresql.org/download/))
- **OpenRouter API Key** ([Get one](https://openrouter.ai/))

### Installation

**1. Clone the repository**
```bash
git clone https://github.com/yourusername/erik-core.git
cd erik-core
```

**2. Create PostgreSQL database**
```bash
# Using psql
createdb erik_db

# Or using PostgreSQL GUI tools
# Database name: erik_db
```

**3. Configure environment variables**

Create `.env` file or set environment variables:
```bash
export DB_USER=your_postgres_username
export DB_PASS=your_postgres_password
export OPENROUTER_API_KEY=your_openrouter_key
```

**4. Configure application.properties**

Edit `src/main/resources/application.properties`:
```properties
# Database connection
spring.datasource.url=jdbc:postgresql://localhost:5432/erik_db
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASS}

# OpenRouter API
erik.api-key=${OPENROUTER_API_KEY}
```

**5. Build and run**
```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The console interface will start and you can begin interacting with Erik!

### First Run

On first run, Hibernate will automatically create all database tables based on the entity annotations. You should see SQL output in the console showing table creation.

---

## Configuration

### Application Properties

**Core Settings:**
```properties
# Spring Boot
spring.application.name=erik-core
spring.main.web-application-type=none
spring.main.banner-mode=off

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/erik_db
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASS}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

**LLM Configuration:**
```properties
# Narrative Model (Claude Sonnet 4.5)
erik.narrative.model=anthropic/claude-sonnet-4.5
erik.narrative.temperature=0.6
erik.narrative.max-tokens=3000

# Analytical Model (Gemini Flash 2.5)
erik.analytical.model=google/gemini-2.5-flash
erik.analytical.temperature=0.3
erik.analytical.max-tokens=6000

# API Configuration
erik.api-key=${OPENROUTER_API_KEY}
```

**Synopsis System:**
```properties
# Keep last 6 exchanges in full detail
erik.round-window-size=6

# Generate synopsis after 18 total exchanges
erik.round-threshold-size=18
```

**Extraction Configuration:**
```properties
# Extract state changes every N exchanges
# 1 = every exchange (most accurate, highest cost)
# 3 = every 3rd exchange (lower cost, slightly less responsive)
erik.extraction.frequency=1

# Enable/disable extraction globally
erik.extraction.enabled=true

# Always extract on first narration (recommended)
erik.extraction.always-extract-on-start=true

# Always extract on final narration (recommended)
erik.extraction.always-extract-on-end=true
```

**Event Compression (Future Feature):**
```properties
# How often to compress old events (0 = never)
erik.events.compress-frequency=20

# How many recent exchanges to keep uncompressed
erik.events.keep-recent-exchanges=10

# Always keep major events uncompressed
erik.events.always-keep-major=true
```

**Development Settings:**
```properties
# Enable debug mode (saves LLM outputs to files)
erik.debug.enabled=true

# Logging
logging.level.com.github.rrousso.erik_core=INFO
logging.level.org.springframework=WARN
logging.level.org.hibernate=WARN
```

---

## Testing

### Current Test Coverage

⚠️ **Test coverage is incomplete.** The following areas have tests:

✅ **FlagDetectorService** (`FlagDetectorServiceTest.java`)
- Input validation tests
- START flag detection (descriptive vs command)
- Context-aware detection
- All lifecycle flags (PAUSE, CONTINUE, END, ABANDON)
- Edge cases and error handling

⚠️ **Areas Without Tests:**
- SessionFlowService and strategies (manual testing only)
- StanzaExtractionService and appliers (manual testing only)
- Database persistence layer (manual testing only)
- Prompt builders (manual testing only)
- End-to-end stanza lifecycle (manual testing only)

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=FlagDetectorServiceTest

# Run with coverage report
mvn test jacoco:report
```

### Manual Testing

The repository includes three test scenarios for manual testing:

**1. Cinderella Stanza** (Basic Lifecycle)
- Tests: START → narration → END
- Validates: Basic flow, character tracking, event logging
- Expected: Clean story progression with proper database updates

**2. Teen Wolf Stanza** (Multi-Character Bleeding)
- Tests: Complex character knowledge tracking
- Validates: Information boundaries, secret awareness states
- Expected: Characters only know what they should know

**3. Beach Stanza** (Full Lifecycle)
- Tests: START → narration → PAUSE → planning → CONTINUE → END
- Validates: Full lifecycle including pause/resume
- Expected: Seamless pause and continuation with state preservation

---

## Development Status

### Completed Features ✅

**Phase 1: Core Infrastructure**
- ✅ Dual-mode system (VOID/STANZA)
- ✅ PostgreSQL database with full entity model
- ✅ Spring Boot application structure
- ✅ LLM integration (Claude + Gemini via OpenRouter)
- ✅ Flag detection system
- ✅ Basic conversation flow

**Phase 2: Strategy Pattern Refactoring**
- ✅ SessionFlowService refactored (500 → 50 lines)
- ✅ StanzaExtractionService refactored (600 → 100 lines)
- ✅ All flow strategies implemented
- ✅ All extraction appliers implemented
- ✅ Configurable extraction frequency

**Phase 3: Stanza Lifecycle**
- ✅ START flag handling
- ✅ PAUSE flag handling
- ✅ CONTINUE flag handling
- ✅ END flag handling
- ✅ ABANDON flag handling
- ✅ Stanza initialization (Phase 1 extraction)
- ✅ Mid-stanza extraction (Phase 2 extraction)
- ✅ Synopsis generation

**Phase 4: Information Boundaries**
- ✅ Character knowledge tracking
- ✅ Secret awareness system (UNAWARE/SUSPICIOUS/KNOWS)
- ✅ User private backstory
- ✅ Fact discovery modes

### In Progress 🔄

**Phase 5: Pre-Narrative Architect**
- 🔄 OOC command detection and handling
- 🔄 Beat transition system (NEXT_BEAT flag)
- 🔄 Dynamic character availability
- 🔄 Tension-driven scene selection

### Planned Features 📋

**Testing & Quality**
- 📋 Comprehensive unit tests for all services
- 📋 Integration tests for full lifecycle
- 📋 Database migration tests
- 📋 Performance testing

**Features**
- 📋 Event compression system
- 📋 Multi-beat stanza support
- 📋 Stanza continuation from database
- 📋 Web interface (currently console only)
- 📋 User authentication
- 📋 Multiple user support

**Optimization**
- 📋 Caching for frequent queries
- 📋 Prompt optimization
- 📋 Cost tracking and reporting
- 📋 Token usage optimization

---

## Known Issues

### High Priority 🔴

1. **Limited Test Coverage**
   - Only FlagDetectorService has comprehensive tests
   - Database layer untested
   - Strategy classes untested
   - Risk: Regressions not caught early

2. **No Error Recovery for Database Failures**
   - If database write fails mid-extraction, state may be inconsistent
   - No transaction rollback handling in strategies
   - Risk: Corrupted stanza state

3. **Extraction Frequency Edge Cases**
   - With frequency > 1, some state changes may be missed
   - No validation that important changes are captured
   - Risk: Narrative divergence from tracked state

### Medium Priority 🟡

4. **Synopsis Generation Not Fully Tested**
   - Long stanzas (30+ exchanges) not tested
   - Synopsis quality not validated
   - Risk: Context loss in long sessions

5. **Character Presence Logic Incomplete**
   - No automatic transition from "potential" to "present"
   - Manual extraction required for presence changes
   - Risk: Character availability inconsistencies

6. **No Stanza Continuation from Database**
   - Code exists but not fully tested
   - Loading past stanzas may have bugs
   - Risk: Can't reliably continue old stories

### Low Priority 🟢

7. **Console Interface Limitations**
   - No command history
   - No editing of previous input
   - Limited error messages
   - Risk: Poor user experience

8. **No Cost Tracking**
   - No logging of API token usage
   - Can't analyze cost per stanza
   - Risk: Unexpected API bills

9. **Prompt Efficiency Not Optimized**
   - Prompts may contain redundant information
   - No compression for large database states
   - Risk: Higher costs, slower responses

---

## Project Structure

```
erik-core/
├── src/main/java/com/github/rrousso/erik_core/
│   ├── controllers/
│   │   └── ConsoleRunner.java                    # User interface
│   ├── domain/
│   │   ├── enums/
│   │   │   ├── Flag.java                         # Lifecycle commands
│   │   │   ├── StanzaStatus.java                 # Status states
│   │   │   └── ModelType.java                    # LLM model types
│   │   └── models/
│   │       ├── SessionState.java                 # In-memory state
│   │       ├── ConversationHistory.java          # Message history
│   │       └── Round.java                        # Exchange tracking
│   ├── dto/
│   │   └── extraction/
│   │       ├── ExtractionResult.java             # Parsed extraction data
│   │       ├── EventExtraction.java              # Event data
│   │       ├── KnowledgeTransfer.java            # Knowledge data
│   │       └── ...                               # Other extraction types
│   ├── persistence/
│   │   ├── entities/
│   │   │   ├── Stanza.java                       # Main container
│   │   │   ├── StanzaCharacter.java              # Characters
│   │   │   ├── Secret.java                       # Secrets
│   │   │   ├── Tension.java                      # Tensions
│   │   │   ├── CharacterKnowledge.java           # Knowledge tracking
│   │   │   ├── CharacterSecretState.java         # Secret awareness
│   │   │   ├── Fact.java                         # Discrete information
│   │   │   └── StanzaEvent.java                  # Event log
│   │   └── repositories/
│   │       └── StanzaRepository.java             # JPA repository
│   ├── services/
│   │   ├── orchestration/
│   │   │   ├── SessionFlowService.java           # Main orchestrator
│   │   │   ├── ConversationService.java          # LLM conversations
│   │   │   ├── StanzaCompletionService.java      # Shared completion
│   │   │   └── strategies/                       # Flow strategies
│   │   ├── stanza/
│   │   │   ├── StanzaExtractionService.java      # Extraction orchestrator
│   │   │   ├── StanzaInitializationService.java  # Initial extraction
│   │   │   ├── StanzaPersistenceService.java     # Database operations
│   │   │   └── appliers/                         # Extraction appliers
│   │   ├── llm/
│   │   │   ├── LLMClientService.java             # OpenRouter API client
│   │   │   └── FlagDetectorService.java          # Command detection
│   │   ├── prompt/
│   │   │   ├── SystemPromptBuilderService.java   # Prompt coordination
│   │   │   ├── ErikSystemPromptBuilder.java      # Erik persona
│   │   │   ├── NarratorSystemPromptBuilder.java  # Narrator persona
│   │   │   └── ExtractionPromptBuilder.java      # Extraction prompts
│   │   └── session/
│   │       └── SynopsisGeneratorService.java     # Synopsis creation
│   ├── util/
│   │   └── JsonCleanupUtil.java                  # JSON parsing helper
│   └── ErikCoreApplication.java                  # Main entry point
├── src/main/resources/
│   ├── application.properties                    # Configuration
│   ├── logback-spring.xml                        # Logging config
│   └── prompts/
│       ├── narrative/
│       │   ├── erik_base_system.txt              # Erik personality
│       │   └── narrator_base_system.txt          # Narrator personality
│       └── analytical/
│           ├── flag_detection.txt                # Command detection
│           └── state_extraction.txt              # Extraction prompt
├── src/test/java/                                # Tests
├── pom.xml                                       # Maven dependencies
└── README.md                                     # This file
```

---

## Contributing

**This project is currently in early development and not accepting contributions yet.**

Once the core functionality is stable and fully tested, contribution guidelines will be added.



**Last Updated:** February 3, 2026  
**Version:** 0.0.1-SNAPSHOT (Unstable)