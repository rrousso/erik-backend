package com.github.rrousso.erik_core.services;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple builder for composing prompts cleanly.
 * Eliminates repetitive StringBuilder.append() calls.
 * 
 * Usage:
 *   String prompt = new PromptComposer()
 *       .section(identityPrompt)
 *       .section(userPersona)
 *       .divider()
 *       .labeledSection("MEMORY:", synopsis, hasSynopsis)
 *       .build();
 */
public class PromptComposer {
    
    private static final String DIVIDER = "---";
    private static final String SECTION_SEPARATOR = "\n\n";
    
    private final List<String> sections = new ArrayList<>();
    
    /**
     * Add a section (always included)
     */
    public PromptComposer section(String content) {
        if (content != null && !content.isEmpty()) {
            sections.add(content);
        }
        return this;
    }
    
    /**
     * Add a section only if condition is true
     */
    public PromptComposer sectionIf(String content, boolean condition) {
        if (condition && content != null && !content.isEmpty()) {
            sections.add(content);
        }
        return this;
    }
    
    /**
     * Add a labeled section: "LABEL:\ncontent"
     */
    public PromptComposer labeledSection(String label, String content) {
        if (content != null && !content.isEmpty()) {
            sections.add(label + "\n" + content);
        }
        return this;
    }
    
    /**
     * Add a labeled section only if condition is true
     */
    public PromptComposer labeledSectionIf(String label, String content, boolean condition) {
        if (condition && content != null && !content.isEmpty()) {
            sections.add(label + "\n" + content);
        }
        return this;
    }
    
    /**
     * Add a divider (---)
     */
    public PromptComposer divider() {
        sections.add(DIVIDER);
        return this;
    }
    
    /**
     * Add a divider only if condition is true
     */
    public PromptComposer dividerIf(boolean condition) {
        if (condition) {
            sections.add(DIVIDER);
        }
        return this;
    }
    
    /**
     * Add a wrapped section with dividers: ---\ncontent\n---
     */
    public PromptComposer wrappedSection(String content) {
        if (content != null && !content.isEmpty()) {
            sections.add(DIVIDER);
            sections.add(content);
            sections.add(DIVIDER);
        }
        return this;
    }
    
    /**
     * Add a wrapped section only if condition is true
     */
    public PromptComposer wrappedSectionIf(String content, boolean condition) {
        if (condition && content != null && !content.isEmpty()) {
            sections.add(DIVIDER);
            sections.add(content);
            sections.add(DIVIDER);
        }
        return this;
    }
    
    /**
     * Add a wrapped labeled section: ---\nLABEL:\ncontent\n---
     */
    public PromptComposer wrappedLabeledSection(String label, String content) {
        if (content != null && !content.isEmpty()) {
            sections.add(DIVIDER);
            sections.add(label + "\n" + content);
            sections.add(DIVIDER);
        }
        return this;
    }
    
    /**
     * Add a wrapped labeled section only if condition is true
     */
    public PromptComposer wrappedLabeledSectionIf(String label, String content, boolean condition) {
        if (condition && content != null && !content.isEmpty()) {
            sections.add(DIVIDER);
            sections.add(label + "\n" + content);
            sections.add(DIVIDER);
        }
        return this;
    }
    
    /**
     * Build the final prompt string
     */
    public String build() {
        return String.join(SECTION_SEPARATOR, sections);
    }
}