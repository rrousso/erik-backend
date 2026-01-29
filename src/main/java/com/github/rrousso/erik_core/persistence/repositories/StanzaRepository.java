package com.github.rrousso.erik_core.persistence.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.rrousso.erik_core.persistence.entities.Stanza;

@Repository
public interface StanzaRepository extends JpaRepository<Stanza, Long> {
    
    // Find by persona
    List<Stanza> findByPersonaId(Long personaId);
    
    // Find active stanza for persona (should only be one)
    @Query("SELECT s FROM Stanza s WHERE s.persona.id = :personaId AND s.status = 'active'")
    Stanza findActiveByPersonaId(@Param("personaId") Long personaId);
    
    // Find by status
    List<Stanza> findByStatus(String status);
    
    // Find by world identifier
    List<Stanza> findByWorldIdentifier(String worldIdentifier);
    
    // Search by setting (case-insensitive partial match)
    @Query("SELECT s FROM Stanza s WHERE LOWER(s.setting) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Stanza> searchBySetting(@Param("keyword") String keyword);
    
    // Search by premise
    @Query("SELECT s FROM Stanza s WHERE LOWER(s.premise) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Stanza> searchByPremise(@Param("keyword") String keyword);
    
    // Search by tone
    @Query("SELECT s FROM Stanza s WHERE LOWER(s.tone) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Stanza> searchByTone(@Param("keyword") String keyword);
    
    // Full-text search (requires search_vector to be set up via SQL migration)
    @Query(value = "SELECT * FROM stanzas WHERE search_vector @@ to_tsquery('english', :query)", 
           nativeQuery = true)
    List<Stanza> fullTextSearch(@Param("query") String query);
    
    // Find completed stanzas for a persona
    @Query("SELECT s FROM Stanza s WHERE s.persona.id = :personaId AND s.status = 'completed' ORDER BY s.createdAt DESC")
    List<Stanza> findCompletedByPersonaId(@Param("personaId") Long personaId);
    
 // Search by character name (joins StanzaCharacter relationship)
    @Query("SELECT DISTINCT s FROM Stanza s JOIN s.characters c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Stanza> searchByCharacter(@Param("keyword") String keyword);
}