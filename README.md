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

**STANZA Mode** — Living Narrative
- The Narrator takes over
- Second-person present tense narration
- You act, the world responds
- Characters react based on what they know
- The scene evolves organically

### Commands

Commands work naturally or can be made explicit with double parentheses:

**In Void Mode:**
- `"let's begin"` / `"start"` → Start the stanza
- Natural conversation with Erik

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

Abandoned stanzas allow starting fresh. Completed stanzas end the creative session.

---

## Technical Architecture

### Dual Model System

Erik uses two models for optimal performance:

**Narrative Model** (Claude Sonnet)
- Erik (void mode conversations)
- Narrator (stanza mode narration)
- Detailed synopsis generation
- Temperature: 0.9 (creative)

**Analytical Model** (Gemini Flash)
- Flag detection (command parsing)
- Stanza setup extraction
- Quick synopsis generation
- Change distillation during pause
- World snapshot updates
- Temperature: 0.3 (precise)

### Memory Management

**Rolling Synopsis System:**
- Maintains world state across the conversation
- Updates periodically (configurable threshold)
- Compresses old exchanges into structured snapshots
- Keeps recent messages in full context
- Prevents context window bloat

**World Snapshot Format:**
```
EVENT HISTORY: [chronological major events]
CURRENT STATE - WORLD: [public observable facts]
CURRENT STATE - USER_ONLY: [private user knowledge]
CURRENT STATE - CHARACTERS: [what each character knows/believes]
META: [active rules and OOC conditions]
```

### Information Boundaries

- Void and Stanza have **separate conversation histories**
- Characters only know what they've learned in-scene
- Out-of-character directives `((like this))` modify the scene without being narrated
- Erik personality stays in void mode (distillation prevents bleed)

---

## Tech Stack

- **Language:** Java 17
- **Framework:** Spring Boot 3.2.1
- **LLM API:** OpenRouter
- **Models:** Claude Sonnet 4 (narrative) + Gemini Flash 2.5 (analytical)
- **Interface:** Console (current) — designed to extend to REST API

---

## Running the Project

### Prerequisites
- Java 17+
- Maven
- OpenRouter API key

### Setup

1. Clone the repository
```bash
git clone <repo-url>
cd erik-core
```

2. Set your API key
```bash
export OPENROUTER_API_KEY=your_key_here
```

3. Run the application
```bash
mvn clean spring-boot:run
```

4. First run will prompt you to create a persona (name, pronouns, description)

### Configuration

Edit `src/main/resources/application.properties`:

```properties
# Narrative model (Claude Sonnet - creative)
erik.narrative.model=anthropic/claude-sonnet-4.5
erik.narrative.temperature=0.9
erik.narrative.max-tokens=1500

# Analytical model (Gemini Flash - efficient)
erik.analytical.model=google/gemini-2.5-flash-lite
erik.analytical.temperature=0.3
erik.analytical.max-tokens=500

# Context window settings
erik.round-window-size=2        # Recent messages to keep
erik.round-threshold-size=4     # When to generate synopsis
```

---

## Project Goals

This project exists to explore:

1. **Narrative Design:** How to create emergent, reactive story spaces
2. **State Management:** Clean session handling, mode switching, lifecycle control
3. **Context Efficiency:** Rolling synopsis, dual-model routing, memory compression
4. **Backend Architecture:** Spring Boot services, separation of concerns, extensibility

From a **creative perspective**, Erik explores a different model of human–AI storytelling: treating the AI not as a writer that outputs plot, but as a **world that reacts**.

From a **technical perspective**, it's a serious backend experiment in stateful session design, information boundaries, and context management.

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

🔨 **Next: Phase 2** — Character & Knowledge System
- Persistent character database
- Knowledge extraction per exchange
- Character-specific context injection
- Relationship tracking

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

Key design principles:
- Strong separation between modes (void/stanza)
- Separate conversation histories prevent information bleed
- Dual model routing optimizes cost and quality
- Prompts are external files for easy iteration
- Services are Spring-managed for testability
- State is explicit and centralized