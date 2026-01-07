# Erik — Narrative Simulation Engine (Java / Spring Boot)

## Overview
**Erik** is an experimental narrative simulation engine built in Java.
It explores how AI-driven characters can participate in structured narrative “stanzas”, combining system rules, player input, and character-aware responses.

The project focuses on **clean backend architecture**, **stateful interactions**, and **extensibility**, rather than UI.

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

Erik is both:
- a technical playground for backend architecture
- a creative experiment in narrative systems

It is designed to be understandable, extendable, and easy to reason about.

---

## Author

**Rafael Rousso**  
Backend / Java Developer  
Argentina

---

## Notes for Reviewers

This project is intentionally small and focused.
It prioritizes clarity of design over completeness.
