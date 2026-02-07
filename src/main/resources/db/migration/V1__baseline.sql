-- =============================================
-- V1__baseline.sql
-- Baseline migration: captures existing schema
-- Generated from Hibernate schema export 2026-02-06
-- =============================================

-- Personas (users of the system)
CREATE TABLE IF NOT EXISTS personas (
    id BIGSERIAL NOT NULL,
    name VARCHAR(255) NOT NULL,
    pronouns VARCHAR(255),
    description VARCHAR(1000),
    other_details VARCHAR(1000),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    PRIMARY KEY (id)
);

-- Stanzas (narrative sessions)
CREATE TABLE IF NOT EXISTS stanzas (
    id BIGSERIAL NOT NULL,
    persona_id BIGINT NOT NULL,
    world_identifier VARCHAR(100),
    status VARCHAR(20),
    current_beat INTEGER,
    current_exchange INTEGER,
    time_context VARCHAR(500),
    world_state VARCHAR(1000),
    world_rules TEXT[],
    locations JSONB,
    setting VARCHAR(500),
    premise VARCHAR(1000),
    tone VARCHAR(200),
    quick_synopsis VARCHAR(2000),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_stanzas_persona FOREIGN KEY (persona_id) REFERENCES personas(id)
);

-- Beats (scenes within a stanza)
CREATE TABLE IF NOT EXISTS beats (
    id BIGSERIAL NOT NULL,
    stanza_id BIGINT NOT NULL,
    beat_number INTEGER NOT NULL,
    start_exchange INTEGER NOT NULL,
    end_exchange INTEGER,
    transition_context TEXT,
    summary TEXT,
    created_at TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_beats_stanza FOREIGN KEY (stanza_id) REFERENCES stanzas(id)
);

-- Characters in a stanza
CREATE TABLE IF NOT EXISTS stanza_characters (
    id BIGSERIAL NOT NULL,
    stanza_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_user BOOLEAN,
    canon_role VARCHAR(300),
    presence_status VARCHAR(20),
    current_location VARCHAR(200),
    public_role VARCHAR(500),
    private_backstory VARCHAR(2000),
    visible_traits TEXT[],
    emotional_state VARCHAR(300),
    motivations TEXT[],
    relationship_to_user VARCHAR(300),
    goals TEXT[],
    blueprint_tier1_essentials TEXT,
    blueprint_tier2_motivators TEXT,
    blueprint_tier3_anchors TEXT[],
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_characters_stanza FOREIGN KEY (stanza_id) REFERENCES stanzas(id)
);

-- Facts (discrete pieces of information)
CREATE TABLE IF NOT EXISTS stanza_facts (
    id BIGSERIAL NOT NULL,
    stanza_id BIGINT NOT NULL,
    fact_key VARCHAR(50) NOT NULL,
    predicate VARCHAR(100) NOT NULL,
    fact_value JSONB,
    kind VARCHAR(30) NOT NULL,
    source VARCHAR(30),
    subject_type VARCHAR(30),
    subject_id VARCHAR(100),
    allowed_reveal_modes VARCHAR(200),
    created_beat INTEGER,
    created_exchange INTEGER,
    created_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_facts_stanza FOREIGN KEY (stanza_id) REFERENCES stanzas(id)
);

-- Character knowledge (what characters know about facts)
CREATE TABLE IF NOT EXISTS character_knowledge (
    id BIGSERIAL NOT NULL,
    character_id BIGINT NOT NULL,
    fact_id BIGINT NOT NULL,
    awareness_state VARCHAR(20) NOT NULL,
    how VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    evidence_facts VARCHAR(500),
    learned_beat INTEGER,
    learned_exchange INTEGER,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE (character_id, fact_id),
    CONSTRAINT fk_knowledge_character FOREIGN KEY (character_id) REFERENCES stanza_characters(id),
    CONSTRAINT fk_knowledge_fact FOREIGN KEY (fact_id) REFERENCES stanza_facts(id)
);

-- Events (what happened in the narrative)
CREATE TABLE IF NOT EXISTS stanza_events (
    id BIGSERIAL NOT NULL,
    stanza_id BIGINT NOT NULL,
    beat_id BIGINT,
    description VARCHAR(280) NOT NULL,
    beat_number INTEGER,
    exchange_number INTEGER,
    involved_characters VARCHAR(300),
    is_major BOOLEAN,
    created_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_events_stanza FOREIGN KEY (stanza_id) REFERENCES stanzas(id),
    CONSTRAINT fk_events_beat FOREIGN KEY (beat_id) REFERENCES beats(id)
);

-- Tensions (narrative threads)
CREATE TABLE IF NOT EXISTS stanza_tensions (
    id BIGSERIAL NOT NULL,
    stanza_id BIGINT NOT NULL,
    description VARCHAR(500) NOT NULL,
    involved_characters VARCHAR(500),
    pressure INTEGER NOT NULL,
    potential_triggers VARCHAR(1000),
    source VARCHAR(30),
    status VARCHAR(20) NOT NULL,
    created_beat INTEGER,
    updated_beat INTEGER,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_tensions_stanza FOREIGN KEY (stanza_id) REFERENCES stanzas(id)
);