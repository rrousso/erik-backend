# Erik - AI-Driven Creative Narrative System

**Erik** is a Java Spring Boot application that provides an interactive storytelling environment powered by dual LLM architecture. It distinguishes between planning conversations (void mode with Erik) and active storytelling (stanza mode with the Narrator), maintaining strict information boundaries and persistent world state.

**Latest Updates (January 2026):**
- ✅ **Strategy Pattern Refactoring** - SessionFlowService and StanzaExtractionService fully refactored
- ✅ **Configurable Extraction Frequency** - Control cost vs accuracy with `erik.extraction.frequency`
- ✅ **Phase 2 Complete** - Mid-stanza state extraction fully implemented
- 🔄 **Phase 3 In Design** - Pre-narrative architect for OOC commands

---

## Table of Contents

- [Core Concept](#core-concept)
- [System Architecture](#system-architecture)
- [Latest Refactorings](#latest-refactorings)
- [Stanza Lifecycle](#stanza-lifecycle)
- [Stanza Memory & Continuation](#stanza-memory--continuation)
- [Technical Architecture](#technical-architecture)
- [Tech Stack](#tech-stack)
- [Running the Project](#running-the-project)
- [Configuration](#configuration)
- [Testing](#testing)

---

## Core Concept

### The Two Modes

**VOID MODE (Planning with Erik):**
- Erik is your creative collaborator
- Discuss ideas, set up stanzas, reflect on experiences
- No narrative action - pure planning and conversation
- Separate conversation history (prevents personality bleed)

**STANZA MODE (Active Storytelling):**
- The Narrator controls the fictional world
- You experience the story as your character
- Strict information boundaries prevent knowledge bleeding
- Rolling synopsis maintains world state
- **Database persistence** tracks all changes in real-time

### What Makes Erik Different

1. **Information Boundaries**
   - Characters only know what they've observed or been told
   - User's private backstory is hidden from characters
   - Separate void/stanza histories prevent context leaking

2. **Dual Model Architecture**
   - Claude Sonnet 4.5 for creative narrative (Erik + Narrator)
   - Gemini Flash 2.5 for analytical tasks (flag detection, extraction)
   - Optimizes for quality and cost

3. **Persistent World State** (Phase 1 & 2 Complete)
   - PostgreSQL database tracks everything
   - Characters, secrets, tensions, events, knowledge
   - Mid-stanza updates via intelligent extraction
   - Load previous stanzas for continuation

4. **Strategy Pattern Architecture** (New!)
   - Clean, maintainable, testable code
   - Each operation isolated in its own strategy
   - Easy to extend without modifying existing code

---

## System Architecture

### Service Layer (After Refactoring)

**Orchestration:**
```
services/orchestration/
├── SessionFlowService          # Main orchestrator (50 lines!)
├── ConversationService         # Unified LLM conversation handling
├── StanzaCompletionService     # Shared stanza completion logic
└── strategies/
    ├── FlowStrategy            # Interface
    ├── FlowStrategyFactory     # Selects appropriate strategy
    ├── VoidModeStrategy        # Regular Erik conversation
    ├── StanzaModeStrategy      # Regular Narrator narration
    ├── StartStanzaStrategy     # Handle START flag
    ├── PauseStanzaStrategy     # Handle PAUSE flag
    ├── ContinueStanzaStrategy  # Handle CONTINUE flag
    ├── EndStanzaStrategy       # Handle END flag
    └── AbandonStanzaStrategy   # Handle ABANDON flag
```

**State Extraction:**
```
services/stanza/
├── StanzaExtractionService     # Orchestrator (100 lines!)
└── appliers/
    ├── ExtractionApplier<T>           # Generic interface
    ├── ExtractionApplierRegistry      # Type-safe registry
    ├── EventApplier                   # Apply events
    ├── KnowledgeTransferApplier       # Apply knowledge
    ├── SecretRevelationApplier        # Apply secrets
    ├── TensionChangeApplier           # Apply tensions
    └── CharacterAppearanceApplier     # Apply appearances
```

### Refactoring Benefits

**Before:**
- SessionFlowService: 500+ lines with complex branching
- StanzaExtractionService: 600+ lines with 5 large methods
- Code duplication (callErik/callNarrator repeated)

**After:**
- SessionFlowService: 50 lines, clean delegation
- StanzaExtractionService: 100 lines, clean delegation
- Zero duplication, each class has one job
- Easy to test each strategy independently
- Adding new features = adding new strategy classes

---

## Latest Refactorings

### 1. Strategy Pattern for SessionFlowService

**What Changed:**
- Removed 500+ lines of complex switch/if-else logic
- Each flow operation is now a separate strategy class
- Factory pattern selects the right strategy
- Conversation logic unified in ConversationService

**Benefits:**
- 90% code reduction in main service
- Zero duplication
- Open/Closed Principle compliance
- Easy to add new flags without modifying existing code

### 2. Strategy Pattern for StanzaExtractionService

**What Changed:**
- Removed 600+ lines of extraction logic
- Each extraction type has its own applier
- Registry pattern provides type-safe application
- Generic interface ensures type safety

**Benefits:**
- 85% code reduction in main service
- Each applier independently testable
- Easy to add new extraction types
- Type-safe compiler checks

### 3. Configurable Extraction Frequency

**What Changed:**
- Extraction is now configurable via properties
- Can extract every N exchanges instead of every exchange
- Always extract on start/end (configurable)
- Allows experimentation with cost vs accuracy

**Configuration:**
```properties
# Extract every 3rd exchange (67% cost savings)
erik.extraction.frequency=3

# Global enable/disable
erik.extraction.enabled=true

# Force extraction on start/end
erik.extraction.always-extract-on-start=true
erik.extraction.always-extract-on-end=true
```

**Benefits:**
- Control API costs
- Experiment with narrator resilience
- Different settings for dev vs prod
- Detailed logging shows when extraction occurs

---

## Stanza Lifecycle

### Phase 1: Planning (VOID Mode)
```
User: "I want to play a male Cinderella at the ball"
Erik: "Interesting! Who should be present?"
User: "The prince, my stepsisters, and the fairy godmother"
Erik: "What secrets are hidden?"
User: "I'm actually a prince from another kingdom in disguise"
```

### Phase 2: Initialization
```
User: "Let's begin!"
→ Gemini extracts stanza setup
→ Database stores:
  - Characters (user, prince, stepsisters, fairy godmother)
  - Secrets (user's true identity)
  - Tensions (will identity be revealed?)
  - Initial world state
```

### Phase 3: Active Narration (STANZA Mode)
```
User: "I approach the prince nervously"
→ Narrator: "The prince's eyes meet yours across the ballroom..."
→ Exchange counter increments
→ Extraction service analyzes exchange (configurable frequency)
→ Database updates:
  - Events: "User approached the prince"
  - Character presence: Prince now "present"
  - Tension pressure might increase
```

### Phase 4: Pause & Reflect
```
User: "((pause, I want the fairy godmother to appear))"
→ Switches to VOID mode
→ Status: PAUSED
Erik: "Want me to bring her in when we continue?"
User: "Yes, and make the stepsisters suspicious"
→ Changes extracted for next continuation
```

### Phase 5: Continuation
```
User: "Yeah, let's continue"
→ Switches back to STANZA mode
→ Status: ACTIVE
→ Narrator incorporates requested changes
→ Extraction continues tracking state
```

### Phase 6: Completion
```
User: "((end stanza))"
→ Narrator provides closing narration
→ Final extraction (if configured)
→ Quick synopsis generated
→ Status: COMPLETED
→ Everything saved to database
Erik: "That was a beautiful story! What did you think?"
```

---

## Stanza Memory & Continuation

### Loading Previous Stanzas

⚠️ **EXPERIMENTAL FEATURE — UNDER TESTING**

Load previously completed stanzas:

```bash
> /list                    # See all saved stanzas
> /search vampire romance  # Find by keywords
> /load 5                  # Load stanza #5 into memory
```

Once loaded, Erik can reference that stanza when planning new ones.

### Search Capabilities

```bash
/search vampire romance          # Full-text search
/search setting:castle           # Search by field
/search character:Derek          # Search by character
```

---

## Technical Architecture

### Dual Model System

**Narrative Model** (Claude Sonnet 4.5):
- Erik (void mode)
- Narrator (stanza mode)
- Temperature: 0.6

**Analytical Model** (Gemini Flash 2.5):
- Flag detection
- Stanza initialization extraction
- Mid-stanza state extraction
- Synopsis generation
- Temperature: 0.3

### Database Schema (PostgreSQL)

**Core Entities:**
- `persona` - User identity
- `stanza_records` - Completed stanzas
- `stanza` - Active/paused stanzas (Phase 2)
- `stanza_character` - Characters in stanza
- `fact` - Atomic truths
- `secret` - Locked facts
- `character_knowledge` - Who knows what
- `character_secret_state` - Secret awareness
- `tension` - Story threads
- `stanza_event` - Events that occurred

### State Extraction (Phase 2 - Complete)

**After each narrator response (configurable):**
1. Build extraction prompt with current DB state
2. Call Gemini to analyze the exchange
3. Parse JSON response into structured data
4. Apply changes via specialized appliers:
   - Events → StanzaEvent entries
   - Knowledge → CharacterKnowledge + Fact entries
   - Secrets → CharacterSecretState updates
   - Tensions → Tension pressure/status changes
   - Appearances → Character presence updates

**Extraction Types:**
- **Events**: What happened ("Derek revealed his identity")
- **Knowledge Transfers**: Who learned what ("Stiles learned Scott is a werewolf")
- **Secret Revelations**: Secrets exposed/suspected (UNAWARE → SUSPICIOUS → KNOWS)
- **Tension Changes**: Story threads escalate/resolve/emerge
- **Character Appearances**: Who entered/left the scene

---

## Tech Stack

- **Language:** Java 17
- **Framework:** Spring Boot 3.2.1
- **Database:** PostgreSQL (JPA/Hibernate)
- **LLM API:** OpenRouter
- **Models:** 
  - Claude Sonnet 4.5 (narrative)
  - Gemini Flash 2.5 (analytical)
- **Testing:** JUnit 5 + Mockito

---

## Running the Project

### Prerequisites
- Java 17+
- Maven
- PostgreSQL
- OpenRouter API key

### Quick Start

1. **Clone and configure:**
```bash
git clone <repository>
cd erik-core

# Configure application.properties
cp src/main/resources/application.properties.template src/main/resources/application.properties
# Edit with your database credentials and API key
```

2. **Database setup:**
```bash
# Create database
createdb erik_db

# Run migrations (auto-handled by Hibernate on first run)
```

3. **Run:**
```bash
mvn spring-boot:run
```

---

## Configuration

### Core Settings (application.properties)

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/erik_db
spring.datasource.username=your_username
spring.datasource.password=your_password

# LLM API
erik.api.key=your_openrouter_key
erik.api.base-url=https://openrouter.ai/api/v1

# Models
erik.narrative-model=anthropic/claude-sonnet-4.5
erik.analytical-model=google/gemini-flash-2.5

# Synopsis System
erik.round-window-size=6        # Recent exchanges to keep
erik.round-threshold-size=18    # When to generate synopsis

# Extraction Configuration (NEW!)
erik.extraction.frequency=1                    # Extract every N exchanges
erik.extraction.enabled=true                   # Global on/off
erik.extraction.always-extract-on-start=true   # Force extract opening
erik.extraction.always-extract-on-end=true     # Force extract closing
```

### Extraction Frequency Guide

**Every exchange (default):**
```properties
erik.extraction.frequency=1
```
- Highest accuracy
- Highest cost
- Real-time state updates

**Every 3rd exchange (balanced):**
```properties
erik.extraction.frequency=3
```
- 67% cost savings
- Good accuracy
- Slight lag in state updates

**Only start/end (minimal):**
```properties
erik.extraction.frequency=999
erik.extraction.always-extract-on-start=true
erik.extraction.always-extract-on-end=true
```
- 90% cost savings
- Experimental accuracy
- Good for short stanzas

---

## Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=SessionFlowServiceTest

# Run with coverage
mvn clean test jacoco:report
```

**Test Coverage:**
- ✅ ConversationHistoryTest
- ✅ FlagDetectorServiceTest
- ✅ SessionFlowServiceTest (refactored)
- ✅ StanzaExtractionServiceTest
- ✅ SynopsisGeneratorServiceTest
- ✅ CommandServiceTest
- 🔄 Strategy tests (in progress)

---

## Current Status

| Phase | Status | Notes |
|-------|--------|-------|
| **Phase 1** | ✅ Complete | Persistence foundation |
| **Phase 2** | ✅ Complete | Mid-stanza extraction |
| **Refactoring** | ✅ Complete | Strategy patterns applied |
| **Phase 3** | 📋 Designed | Pre-narrative architect (OOC commands) |

---

## Why This Project Exists

Erik explores a different model of human-AI storytelling:

**Not:** AI writes plot for you to read  
**Instead:** AI creates a world that reacts to YOU

The user is present INSIDE the scene, not directing from outside.

---

## Architecture Principles

- **Clear separation** between void/stanza modes
- **Information boundaries** prevent knowledge bleeding
- **Strategy patterns** for maintainability
- **Database persistence** for world memory
- **Dual model routing** optimizes cost/quality
- **External prompts** for easy iteration
- **SOLID principles** throughout

---

## Author

**Rafael Rousso**  
Backend / Java Developer  
Buenos Aires, Argentina
