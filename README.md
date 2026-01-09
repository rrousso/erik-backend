# Erik — Narrative Simulation Engine (Java / Spring Boot)

## Overview

**Erik** is not a story generator.

It is a **narrative simulation**: a space you enter, where an AI narrator embodies a living scene that responds to what you do, not to what you ask for.

During a session (called a *stanza*), the system creates a consistent fictional world with rules, memory, and emotional continuity.  
The narrator does not prompt you with “what happens next?” — the world waits for you to act inside it.

This makes Erik closer to a **theatrical space** or **roleplay engine** than to a traditional interactive fiction tool.

---

## How to Experience Erik

Erik is not used like a chatbot.

Once a stanza begins, you are inside a living narrative scene.  
The narrator will describe what exists and what happens — but it will not ask you what to do next.

You interact by **acting inside the world**:
- speak to characters
- move
- make decisions
- introduce intentions

The world advances in response to what you do, not to prompts like “continue the story”.

---

## Example Interaction

[Erik]
"Oh, hello! Would you like to work on shaping a story together?"

> "I'm walking along the beach shore and see something shining in the distance."

[Narration]
The morning air carries that familiar salt-spray bite you've come to associate with your beach walks...
A glint of light appears and vanishes with each surge of water, like a signal trying to catch your attention.

> "I walk toward the glinting object."

[Narration]
Your shoes leave familiar impressions in the damp sand as you approach...
An angular metallic object lies half-buried, etched with strange symbols.

> "I reach for it."

[Narration]
As your fingers close around it, an unsettling cold seeps into your skin...
The waves go strangely quiet, as if the ocean itself is holding its breath.

---

## Key Concepts

- **Stanza**
  A self-contained narrative session with a clear start and end lifecycle.

- **Narrator Mode**
  During an active stanza, Erik switches to a narrator role and participates in the story.

- **Lifecycle Management**
  Stanzas are explicitly started and ended. Once closed, they cannot be resumed.

- **Rule-driven behavior**
  The system enforces narrative guardrails while allowing creative freedom.

---

## Tech Stack

- Java 17
- Spring Boot
- Maven
- Console-based interface (current)
- Designed to be extended to REST / Web APIs

---

## Project Goals

- Explore narrative state management
- Practice clean backend design in Java
- Build a project that can evolve into:
  - REST APIs
  - Event-driven flows
  - Multiple characters
  - Persistence layers

This project is intentionally **incremental** and **learning-oriented**.

---

## Running the Project

### Prerequisites
- Java 17+
- Maven

### Run locally
```bash
mvn clean spring-boot:run
```

The application runs in console mode and guides you through starting and interacting with a stanza.

---

## Example Flow

1. Start the application
2. Erik greets the user
3. You propose a narrative idea
4. Start a stanza
5. Interact with the narrator
6. End the stanza explicitly

---

## Current Status

- Core stanza lifecycle implemented
- Console interaction working
- Narrative rules enforced

### Planned Improvements
- REST API interface
- Persistence for stanzas
- Multiple character support
- Event-driven architecture
- Better separation of concerns

---

## Why This Project Exists

Erik exists to explore a different model of human–AI storytelling:

Instead of treating the AI as a writer that outputs plot,  
Erik treats the AI as a **world that reacts**.

The user is not directing a story.
They are **present inside a scene**.

From a technical perspective, this project is also a serious backend experiment in:
- stateful session design
- lifecycle management
- memory reduction via synopsis
- strict role separation between system, narrator, and user

It is designed as both:
- a creative narrative system
- and a clean, extensible Spring Boot architecture.

---

## Author

**Rafael Rousso**  
Backend / Java Developer  
Argentina

---

## Notes for Reviewers

This project is intentionally small and focused.
It prioritizes clarity of design over completeness.
