package com.github.rrousso.erik_core.persistence.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "personas")
public class Persona {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;

    private String pronouns;
    
    @Column(length = 1000)
    private String description;
    
    @Column(length = 1000)
    private String otherDetails;
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp 
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "persona", cascade = CascadeType.ALL)
    private List<Stanza> stanzas = new ArrayList<>();
    
    public Persona() {
    }
    
    public Persona(String name, String pronouns, String description, String otherDetails) {
        this.name = name;
        this.pronouns = pronouns;
        this.description = description;
        this.otherDetails = otherDetails;
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPronouns() {
		return pronouns;
	}

	public void setPronouns(String pronouns) {
		this.pronouns = pronouns;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getOtherDetails() {
		return otherDetails;
	}

	public void setOtherDetails(String otherDetails) {
		this.otherDetails = otherDetails;
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

	public List<Stanza> getStanzas() {
		return stanzas;
	}

	public void setStanzas(List<Stanza> stanzas) {
		this.stanzas = stanzas;
	}

}