package com.github.rrousso.erik_core.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;

@Entity
@Table(name = "stanza_records")
public class StanzaRecord {
	
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;  // THIS IS THE PERSONA ENTITY, NOT STRING!
    
    @Column(length = 2000)
    private String quickSynopsis;
    
    @Lob  
    @Column(columnDefinition = "TEXT")
    private String detailedSynopsis;
    
    // StanzaSetup fields
    @Column(length = 500)
    private String setting;
    
    @Column(length = 1000)
    private String premise;
    
    @Column(length = 500)
    private String userRole;
    
    @Column(length = 500)
    private String userBackstory;
    
    @Column(length = 200)
    private String tone;
    
    // Lists need @ElementCollection
    @ElementCollection
    @CollectionTable(name = "stanza_characters", joinColumns = @JoinColumn(name = "stanza_id"))
    @Column(name = "character_name")
    private List<String> characters = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "stanza_rules", joinColumns = @JoinColumn(name = "stanza_id"))
    @Column(name = "rule")
    private List<String> specialRules = new ArrayList<>();
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp 
    private LocalDateTime updatedAt;

    // No-args constructor
    public StanzaRecord() {
    }
    
    // Useful constructor
    public StanzaRecord(Persona persona, String quickSynopsis, String detailedSynopsis) {
        this.persona = persona;
        this.quickSynopsis = quickSynopsis;
        this.detailedSynopsis = detailedSynopsis;
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Persona getPersona() {
		return persona;
	}

	public void setPersona(Persona persona) {
		this.persona = persona;
	}

	public String getQuickSynopsis() {
		return quickSynopsis;
	}

	public void setQuickSynopsis(String quickSynopsis) {
		this.quickSynopsis = quickSynopsis;
	}

	public String getDetailedSynopsis() {
		return detailedSynopsis;
	}

	public void setDetailedSynopsis(String detailedSynopsis) {
		this.detailedSynopsis = detailedSynopsis;
	}

	public String getSetting() {
		return setting;
	}

	public void setSetting(String setting) {
		this.setting = setting;
	}

	public String getPremise() {
		return premise;
	}

	public void setPremise(String premise) {
		this.premise = premise;
	}

	public String getUserRole() {
		return userRole;
	}

	public void setUserRole(String userRole) {
		this.userRole = userRole;
	}
	
	public String getUserBackStory() {
		return userBackstory;
	}

	public void setUserBackstory(String userBackstory) {
		this.userBackstory = userBackstory;
	}

	public String getTone() {
		return tone;
	}

	public void setTone(String tone) {
		this.tone = tone;
	}

	public List<String> getCharacters() {
		return characters;
	}

	public void setCharacters(List<String> characters) {
		this.characters = characters;
	}

	public List<String> getSpecialRules() {
		return specialRules;
	}

	public void setSpecialRules(List<String> specialRules) {
		this.specialRules = specialRules;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

    
}