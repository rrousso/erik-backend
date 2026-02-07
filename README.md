# Erik - AI-Driven Creative Narrative System

> ⚠️ **UNSTABLE — NOT PRODUCTION-READY**
>
> Active development. Core features work but may have bugs. Schema, API behavior, and prompts may change without notice.
> **Development and experimentation only.**

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

**Erik** is a Java Spring Boot console application for interactive storytelling, powered by a dual-LLM architecture. It enforces strict separation between planning (VOID mode with Erik) and narration (STANZA mode with the Narrator), while tracking persistent narrative state — characters, facts, knowledge boundaries, tensions, and events — in a PostgreSQL database.

### Core Capabilities

- **Dual Mode System** — VOID mode for story planning with Erik; STANZA mode for live narration with the Narrator. Separate conversation histories prevent personality and knowledge bleed between modes.
- **Fact-Based Information Boundaries** — A `Fact` + `CharacterKnowledge` system tracks what each character knows, how they learned it, and what they're still unaware of. Facts can be restricted with discovery rules (TOLD, OBSERVED, INFERRED, SENSED_SPECIAL, DOCUMENTED). Characters can only act on information they actually possess.
- **Beat System** — User-controlled scene boundaries within a stanza. Beats mark transitions in location, time, or perspective. When a beat ends, its events are summarized into prose and minor events are pruned, keeping context manageable over long sessions.
- **Persistent World State** — PostgreSQL tracks characters, facts, tensions, events, beats, and knowledge states in real-time via mid-stanza extraction after every exchange.
- **Strategy Pattern Architecture** — SessionFlowService (~50 lines) delegates all logic to isolated strategy classes. StanzaExtractionService (~100 lines) delegates to typed appliers. Easy to extend.
- **OOC (Out-of-Character) Directives** — Users can issue invisible instructions to the Narrator via `((double parentheses))` syntax for narrative adjustments, system commands, and perspective shifts.
- **Slash Commands** — Deterministic `/help`, `/list`, `/search`, `/load`, `/clear`, `/debug` commands bypass LLM processing entirely for database operations and inspection.

---

## How It Works

### Dual-LLM Architecture

Erik routes tasks to two different AI models via OpenRouter:

**Gemini 2.5 Pro** (Narrative) — Powers both Erik and the Narrator for all creative content. Temperature: 0.4. Used for planning discussions, narration, and synopsis generation.

**Gemini 2.5 Flash** (Analytical) — Handles flag detection (START, PAUSE, END, etc.), state extraction from narrative exchanges, synopsis compression, and beat summaries. Temperature: 0.3.

Models are configured in `application.properties` under `erik.narrative.model` and `erik.analytical.model` and can be swapped to any OpenRouter-compatible model.

### Two-Mode System

**VOID Mode** — The user talks to Erik, a creative collaborator who exists outside the simulation. Together they plan the world, characters, tensions, and setup for a stanza. Erik has dedicated directive prompts for each session state (planning, paused, completed, abandoned). Void mode has its own conversation history.

**STANZA Mode** — The Narrator controls the fictional world. The user experiences the story as their character. Every exchange triggers state extraction (via the analytical model) that updates the database. Stanza mode has its own separate conversation history.

Transitions between modes are triggered by natural-language flags detected by the analytical model (e.g., "let's begin" → START_STANZA, "pause" → PAUSE_STANZA).

### The Information Boundary System

Information boundaries are enforced through several layers:

**Fact Registry** — Each stanza maintains a registry of `Fact` entities. Facts can be WORLD (public knowledge), USER_PRIVATE (narrator-only), USER_PUBLIC (observable), or EMERGENT (discovered during narration). Restricted facts have `allowedRevealModes` that constrain how characters can learn them.

**Character Knowledge** — `CharacterKnowledge` records link characters to facts with an awareness state (KNOWS, SUSPICIOUS, or absent = UNAWARE) and a learning method (TOLD, OBSERVED, INFERRED, etc.). The Narrator prompt displays each character's knowledge state using fact hashes so the model knows exactly what each character can act on.

**Separate Histories** — VOID and STANZA conversation histories are completely isolated, preventing Erik's planning personality from bleeding into narration and vice versa.

**User Private Backstory** — Stored on the user's `StanzaCharacter` entity. Visible to the Narrator for dramatic irony but invisible to NPC characters.

### The Extraction System

After each narrative exchange, `StanzaExtractionService` sends the conversation to the analytical model along with current database state. The model returns structured JSON identifying what changed: new events, fact discoveries, knowledge transfers, tension shifts, character appearances, blueprint updates, and emergent characters. Each extraction type has a dedicated `ExtractionApplier` that validates and persists the changes.

Extraction frequency is configurable (`erik.extraction.frequency`) with options to force extraction at stanza start/end and beat boundaries.

### The Beat System

Beats are user-controlled scene divisions within a stanza. Users trigger transitions with `((next beat: transition context))` syntax. The system:

1. Processes any closing narration for the current beat
2. Forces extraction to capture final state
3. Generates a prose summary of the completed beat (via `BeatSummaryService`)
4. Prunes minor events from the completed beat (major events preserved)
5. Starts a new beat with fresh context
6. Generates opening narration for the new scene
7. Clears rolling synopsis for a fresh compression window

Completed beat summaries replace individual events in the Narrator's context window, keeping prompt size manageable across long sessions.

### Synopsis Pipeline

The system maintains narrative continuity through three synopsis types:

**Rolling Synopsis** — Continuously updated compression of the current beat's events and exchanges. Generated from extracted database events (source of truth) plus recent exchanges (for narrative flavor). Updated based on configurable window/threshold sizes.

**Quick Synopsis** — Generated when a stanza ends or is abandoned. Combines completed beat summaries + rolling synopsis + final exchanges into a brief (~200 word) narrative recap. Saved to the stanza's `quick_synopsis` field for `/list` and `/load` display.

**Detailed Synopsis** — A structured factual record of the completed stanza, including character list, chronological events, and current status. Used for stanza continuation and reference.

---

## System Architecture

### Request Flow

```
User Input
    │
    ├──→ CommandService (/slash commands — bypasses LLM entirely)
    │
    └──→ SessionFlowService (main orchestrator, ~50 lines)
            │
            ├──→ FlagDetectorService (Gemini Flash — detects START/PAUSE/END/etc.)
            │
            └──→ FlowStrategyFactory
                    │
                    ├──→ VoidModeStrategy ──→ ConversationService (Erik)
                    ├──→ StanzaModeStrategy ──→ ConversationService (Narrator)
                    │                              └──→ StanzaExtractionService
                    ├──→ StartStanzaStrategy ──→ StanzaInitializationService
                    ├──→ PauseStanzaStrategy
                    ├──→ ContinueStanzaStrategy
                    ├──→ EndStanzaStrategy ──→ StanzaCompletionService
                    ├──→ AbandonStanzaStrategy ──→ StanzaCompletionService
                    └──→ NextBeatStrategy ──→ BeatTransitionService
```

### Service Layer

```
services/
├── orchestration/
│   ├── SessionFlowService.java              # Main entry point (~50 lines)
│   ├── ConversationService.java             # Unified LLM conversation handler
│   ├── StanzaCompletionService.java         # Shared end/abandon logic
│   └── strategies/
│       ├── FlowStrategy.java                # Interface
│       ├── FlowStrategyFactory.java         # Routes flags/modes to strategies
│       ├── VoidModeStrategy.java
│       ├── StanzaModeStrategy.java
│       ├── StartStanzaStrategy.java
│       ├── PauseStanzaStrategy.java
│       ├── ContinueStanzaStrategy.java
│       ├── EndStanzaStrategy.java
│       ├── AbandonStanzaStrategy.java
│       └── NextBeatStrategy.java
├── stanza/
│   ├── StanzaExtractionService.java         # Extraction orchestrator (~100 lines)
│   ├── StanzaInitializationService.java     # Phase 1 extraction (stanza setup)
│   ├── StanzaPersistenceService.java        # Database operations
│   ├── BeatTransitionService.java           # Beat close/open/summary pipeline
│   ├── BeatSummaryService.java              # Beat prose summary generation
│   └── appliers/
│       ├── ExtractionApplier.java           # Generic interface
│       ├── ExtractionApplierRegistry.java   # Type-safe registry
│       ├── EventApplier.java
│       ├── FactDiscoveryApplier.java
│       ├── SecretRevelationApplier.java
│       ├── TensionChangeApplier.java
│       ├── CharacterAppearanceApplier.java
│       ├── BlueprintUpdateApplier.java
│       └── EmergentCharacterApplier.java
├── llm/
│   ├── LLMClientService.java               # OpenRouter API client
│   └── FlagDetectorService.java             # Command detection via Gemini Flash
├── prompt/
│   ├── SystemPromptBuilderService.java      # Prompt composition & coordination
│   ├── PromptLoaderService.java             # Loads prompt templates from resources
│   └── ExtractionPromptBuilder.java         # Extraction prompt assembly
├── session/
│   ├── SessionAssemblerService.java         # Builds SessionContext snapshots
│   └── SynopsisGeneratorService.java        # Rolling, quick, and detailed synopses
├── config/
│   ├── PersonaService.java                  # User persona management + first-time setup
│   └── SynopsisConfigService.java           # Synopsis configuration
└── command/
    └── CommandService.java                  # Slash command handler (/help, /list, etc.)
```

### Prompt Templates

```
src/main/resources/prompts/
├── user/
│   └── fictional_frame.txt                  # Safety framing for all modes
├── erik/
│   ├── personality.txt                      # Erik's base personality
│   ├── directive_planning.txt               # VOID mode: planning state
│   ├── directive_paused.txt                 # VOID mode: stanza paused
│   ├── directive_completed.txt              # VOID mode: stanza completed
│   └── directive_abandoned.txt              # VOID mode: stanza abandoned
├── narrator/
│   ├── stanza_narrator.txt                  # Narrator personality + rules
│   └── detailed_synopsis.txt                # End-of-stanza documentation
├── architect/
│   └── initialization_prompt.txt            # Stanza setup extraction
└── analytical/
    ├── flag_detection.txt                   # Flag detection prompt
    ├── state_extraction.txt                 # Mid-stanza extraction prompt
    ├── rolling_synopsis.txt                 # Rolling synopsis generation
    ├── quick_synopsis.txt                   # Quick synopsis generation
    └── beat_summary.txt                     # Beat summary generation
```

### Domain Model

```
persistence/entities/
├── Persona.java                 # User identity (name, pronouns, description)
├── Stanza.java                  # Narrative session (setting, premise, tone, world state)
├── Beat.java                    # Scene boundary within a stanza
├── StanzaCharacter.java         # Character in a stanza (with presence status)
├── Fact.java                    # Discrete information (with discovery rules)
├── CharacterKnowledge.java      # What a character knows about a fact
├── Secret.java                  # Named secrets
├── CharacterSecretState.java    # Character awareness of secrets
├── Tension.java                 # Narrative tension threads
└── StanzaEvent.java             # Event log entries
```

---

## Stanza Lifecycle

```
VOID MODE                          STANZA MODE
(Planning)                         (Narration)
    │                                  │
    │──→ START ──→ Initialize ────────→│
    │                                  │──→ Exchange loop:
    │                                  │     User input → Narrator response
    │                                  │     → Extraction → DB update
    │                                  │     → Synopsis refresh
    │                                  │
    │                                  │──→ NEXT_BEAT:
    │                                  │     Close beat → Summary → Prune
    │                                  │     → Open new beat → Narrate
    │                                  │
    │←── PAUSE ←───────────────────────│
    │     (Erik huddle)                │
    │──→ CONTINUE ────────────────────→│
    │                                  │
    │←── END ←─────────────────────────│
    │     (Quick synopsis + reflect)   │
    │                                  │
    │←── ABANDON ←─────────────────────│
          (Quick synopsis, no reflect)
```

---

## Database Design

PostgreSQL with Flyway migrations (`src/main/resources/db/migration/`). Hibernate validates the schema at startup (`ddl-auto=validate`).

Core tables: `personas`, `stanzas`, `beats`, `stanza_characters`, `stanza_facts`, `character_knowledge`, `secrets`, `character_secret_state`, `stanza_tensions`, `stanza_events`.

Key relationships:
- A `Stanza` belongs to a `Persona` and contains `Beat`s, `StanzaCharacter`s, `Fact`s, `Tension`s, and `StanzaEvent`s
- `CharacterKnowledge` links a `StanzaCharacter` to a `Fact` with awareness state and learning method
- `StanzaEvent`s belong to a `Beat` and track exchange number, involved characters, and major/minor classification
- `Fact`s can be restricted with `allowedRevealModes` (comma-separated: TOLD, OBSERVED, INFERRED, SENSED_SPECIAL, DOCUMENTED)

---

## Tech Stack

- **Java 17** + **Spring Boot 3.2**
- **PostgreSQL** (primary datastore)
- **Flyway** (schema migrations)
- **Spring Data JPA / Hibernate** (ORM)
- **OpenRouter API** (LLM gateway — currently Gemini 2.5 Pro + Flash)
- **Jackson** (JSON parsing for extraction results)
- **Maven** (build)
- **JUnit 5 + Mockito** (testing)

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL 14+
- An [OpenRouter](https://openrouter.ai/) API key

### Setup

**1. Clone the repository**
```bash
git clone https://github.com/yourusername/erik-core.git
cd erik-core
```

**2. Create PostgreSQL database**
```bash
createdb erik_db
```

**3. Set environment variables**
```bash
export DB_USER=your_postgres_username
export DB_PASS=your_postgres_password
export OPENROUTER_API_KEY=your_openrouter_key
```

**4. Build and run**
```bash
mvn clean install
mvn spring-boot:run
```

On first run, Flyway applies the baseline migration and PersonaService runs a first-time setup wizard in the console to create your persona.

---

## Configuration

All configuration is in `src/main/resources/application.properties`:

| Property | Default | Purpose |
|----------|---------|---------|
| `erik.narrative.model` | `google/gemini-2.5-pro` | Model for Erik + Narrator |
| `erik.narrative.temperature` | `0.4` | Creativity level for narrative |
| `erik.analytical.model` | `google/gemini-2.5-flash` | Model for extraction + flags |
| `erik.analytical.temperature` | `0.3` | Precision for analysis |
| `erik.round-window-size` | `6` | Recent exchanges kept in context |
| `erik.round-threshold-size` | `18` | Exchanges before synopsis triggers |
| `erik.extraction.frequency` | `1` | Extract every N exchanges |
| `erik.extraction.enabled` | `true` | Toggle extraction |
| `erik.events.compress-frequency` | `20` | Event compression interval |
| `erik.debug.enabled` | `true` | Debug file output |

---

## Testing

### Current Coverage

Tests exist for:
- `SessionFlowService` — routing logic (flag → strategy, mode → strategy)
- `FlagDetectorService` — flag detection edge cases
- `SessionAssemblerService` — context assembly for VOID and STANZA modes
- `CommandService` — slash command parsing and execution

### Test Stanzas

**Cinderella Stanza** — Basic lifecycle: START → narration → END. Validates flow, character tracking, event logging.

**Teen Wolf Stanza** — Multi-character knowledge tracking. Validates information boundaries and fact discovery.

**Beach Stanza** — Full lifecycle: START → narration → PAUSE → planning → CONTINUE → END. Validates state preservation across pause/resume.

Run tests:
```bash
mvn test
```

---

## Development Status

### Completed ✅

- Dual-mode system (VOID / STANZA) with complete separation
- Strategy pattern architecture (SessionFlowService, ExtractionAppliers)
- Full stanza lifecycle (START, PAUSE, CONTINUE, END, ABANDON)
- Fact-based information boundary system with discovery rules
- Character knowledge tracking (KNOWS / SUSPICIOUS / UNAWARE)
- Beat system with summary generation and event pruning
- OOC directive handling in narrator prompt
- Rolling + quick + detailed synopsis pipeline
- Mid-stanza extraction with typed appliers (events, facts, knowledge, tensions, characters, blueprints, emergent characters)
- Slash commands (/help, /list, /search, /load, /clear, /debug)
- Persona management with first-time setup
- Flyway database migrations
- PostgreSQL full-text search for stanza lookup
- Debug file output (prompts, synopses, extraction results)

### In Progress 🔄

- Dynamic character availability (tension-driven scene selection)
- Comprehensive test coverage across all services
- Stanza continuation from database (loading a previous stanza to resume narrating)

### Planned 📋

- Web interface (currently console only)
- User authentication and multi-user support
- Caching for frequent database queries
- Cost tracking and token usage reporting
- Integration tests for full lifecycle scenarios

---

## Known Issues

1. **Limited test coverage** — Only a few services have comprehensive unit tests. Risk of regressions.
2. **No transaction rollback on extraction failure** — If a database write fails mid-extraction, state may be partially applied.
3. **Console-only interface** — No web UI yet; `spring.main.web-application-type=none` is set.
4. **Single-user system** — PersonaService loads the first persona from the database. No multi-user support.
5. **Synopsis can drift** — Rolling synopsis is LLM-generated compression and may occasionally drop or distort details over very long sessions.

---

## Contributing

This project is in early development and not currently accepting contributions. Contribution guidelines will be added once core functionality is stable.

---

**Last Updated:** February 7, 2026
**Version:** 0.0.1-SNAPSHOT