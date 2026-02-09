# Erik Core — AI-Driven Narrative Simulation (Archived)

> ⚠️ **ARCHIVED — No longer in active development.**
>
> This version explored using LLM-driven fact registries and per-character knowledge tracking to enforce information boundaries in interactive fiction — making NPCs act only on what they actually know, enabling dramatic irony, and preventing "knowledge bleeding" between characters.
>
> The approach hit a fundamental limitation of current transformer architectures: **attention is not access control.** No matter how carefully character knowledge is modeled in a database and injected into prompts, the LLM attends to all tokens in its context window equally. Characters consistently act on information they shouldn't have access to, especially across scene changes or when new characters are introduced.
>
> This is not a prompt engineering problem — it's a property of how transformers process context. The information boundary system works at a data modeling level but cannot be reliably enforced at generation time with current models.
>
> **Active development continues in [erik-lite](https://github.com/rrousso/erik-lite)**, a streamlined version that keeps the parts of this system that work well (dual-mode planning/narration, stanza lifecycle, synopsis persistence, beat system) and drops the fact/knowledge tracking in favor of a simpler narrator-driven approach.

---

## What This Project Contains

This repo is a complete Java Spring Boot application for interactive storytelling with a dual-LLM architecture. It represents roughly 3 months of development and experimentation. The code is functional and tested — the archive is due to a design-level limitation, not broken code.

### What Worked Well

These components are carried forward into erik-lite:

- **Dual Mode System** — VOID mode (planning with Erik) and STANZA mode (narration with the Narrator) with completely separate conversation histories. This prevents personality bleed between the planning companion and the narrator.
- **Stanza Lifecycle** — Full START → narration → PAUSE → planning → CONTINUE → END/ABANDON cycle with state preservation across transitions.
- **Beat System** — User-controlled scene boundaries. When a beat ends, events are summarized into prose and minor events are pruned, keeping context manageable over long sessions.
- **Rolling Synopsis Pipeline** — LLM-generated compression that preserves critical story details across arbitrarily long sessions. Solves the core problem of important character info (pronouns, backstory, established facts) scrolling out of the context window.
- **OOC Directives** — `((double parentheses))` syntax for invisible instructions to the narrator. Lets the user correct mistakes, adjust pacing, or shift perspective without breaking immersion.
- **Strategy Pattern Architecture** — SessionFlowService (~50 lines) delegates to isolated strategy classes. Easy to extend and test.
- **Slash Commands** — Deterministic `/help`, `/list`, `/search`, `/load`, `/clear`, `/debug` bypass LLM processing entirely.

### What Didn't Work (And Why)

These components are being dropped or fundamentally rethought:

- **Fact Registry + CharacterKnowledge** — The `Fact` → `CharacterKnowledge` system (with KNOWS / SUSPICIOUS / UNAWARE states and discovery rules like TOLD, OBSERVED, INFERRED) models information boundaries correctly in the database. But when the full fact registry is injected into the narrator prompt, the LLM uses all of it regardless of per-character access rules. Characters answer questions they shouldn't know the answers to, newly introduced NPCs act on information from scenes they weren't in, and partial information sharing (telling a character *some* facts) gets expanded to full knowledge within one exchange.
- **LLM-Driven Semantic Dedup** — The extraction prompt asks Gemini Flash to check the existing fact registry before creating new facts. This works for exact matches but fails for semantically equivalent statements with different wording. The result is a growing registry of near-duplicates that compounds the context pollution problem.
- **Event/Fact Distinction** — The extraction system treats too many transient events as persistent facts (emoji reactions, individual chat messages, social actions). This inflates the registry with noise, making the semantic dedup problem worse.

### The Core Lesson

If the narrator needs global knowledge for dramatic irony, and characters need partitioned knowledge for believable behavior, you need either:

1. **Multiple isolated generation calls** (one per character, each seeing only their own knowledge) — works in theory but the latency and cost of N calls per exchange is prohibitive with current APIs.
2. **Models that support partitioned attention** — don't exist yet.
3. **Acceptance that the narrator is the narrator**, not a simulation engine — meaning you drop true character cognition and rely on synopsis + user corrections to maintain consistency. This is what erik-lite does.

---

## System Architecture

### Dual-LLM Architecture

Models are routed via OpenRouter:

- **Gemini 2.5 Pro** (Narrative) — Powers Erik and the Narrator. Temperature: 0.4.
- **Gemini 2.5 Flash** (Analytical) — Flag detection, state extraction, synopsis compression, beat summaries. Temperature: 0.3.

Configured in `application.properties` under `erik.narrative.model` and `erik.analytical.model`.

### Project Structure

```
src/main/java/com/github/rrousso/erik_core/
├── services/
│   ├── session/
│   │   ├── SessionFlowService.java          # Strategy orchestrator (~50 lines)
│   │   ├── SessionAssemblerService.java     # Builds SessionContext snapshots
│   │   └── SynopsisGeneratorService.java    # Rolling, quick, and detailed synopses
│   ├── stanza/
│   │   ├── StanzaExtractionService.java     # Mid-stanza extraction coordinator
│   │   └── appliers/                        # Typed extraction appliers
│   ├── llm/
│   │   ├── LLMClientService.java            # OpenRouter API client
│   │   └── FlagDetectorService.java         # Command detection via Flash
│   ├── prompt/
│   │   ├── SystemPromptBuilderService.java  # Prompt composition
│   │   ├── PromptLoaderService.java         # Template loading from resources
│   │   └── ExtractionPromptBuilder.java     # Extraction prompt assembly
│   ├── config/
│   │   └── PersonaService.java              # User persona management
│   └── command/
│       └── CommandService.java              # Slash command handler
├── persistence/entities/
│   ├── Persona.java                         # User identity
│   ├── Stanza.java                          # Narrative session
│   ├── Beat.java                            # Scene boundary
│   ├── StanzaCharacter.java                 # Character with presence status
│   ├── Fact.java                            # Discrete information (with discovery rules)
│   ├── CharacterKnowledge.java              # What a character knows about a fact
│   ├── Tension.java                         # Narrative tension threads
│   └── StanzaEvent.java                     # Event log entries
└── config/
    └── ErikProperties.java                  # Spring Boot configuration
```

### Prompt Templates

```
src/main/resources/prompts/
├── user/fictional_frame.txt                 # Safety framing
├── erik/                                    # Erik personality + state directives
├── narrator/stanza_narrator.txt             # Narrator personality + rules
├── architect/initialization_prompt.txt      # Stanza setup extraction
└── analytical/                              # Flash prompts (extraction, synopsis, flags)
```

### Stanza Lifecycle

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

### Database

PostgreSQL with Flyway migrations (`src/main/resources/db/migration/`). Hibernate validates at startup.

Core tables: `personas`, `stanzas`, `beats`, `stanza_characters`, `stanza_facts`, `character_knowledge`, `secrets`, `character_secret_state`, `stanza_tensions`, `stanza_events`.

---

## Tech Stack

- Java 17 + Spring Boot 3.2
- PostgreSQL + Flyway + Spring Data JPA / Hibernate
- OpenRouter API (Gemini 2.5 Pro + Flash)
- Jackson, Maven, JUnit 5 + Mockito

---

## Running (For Reference)

```bash
# Prerequisites: Java 17+, Maven 3.6+, PostgreSQL 14+, OpenRouter API key

createdb erik_db

export DB_USER=your_postgres_username
export DB_PASS=your_postgres_password
export OPENROUTER_API_KEY=your_openrouter_key

mvn clean install
mvn spring-boot:run
```

---

**Archived:** February 2026
**Version:** 0.0.1-SNAPSHOT
**Author:** rrousso