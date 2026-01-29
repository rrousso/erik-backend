package com.github.rrousso.erik_core.persistence.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.rrousso.erik_core.persistence.entities.Tension;

@Repository
public interface TensionRepository extends JpaRepository<Tension, Long> {
    
    // Find all tensions for a stanza
    List<Tension> findByStanzaId(Long stanzaId);
    
    // Find by status
    @Query("SELECT t FROM Tension t WHERE t.stanza.id = :stanzaId AND t.status = :status")
    List<Tension> findByStatus(@Param("stanzaId") Long stanzaId, @Param("status") String status);
    
    // Find active tensions
    @Query("SELECT t FROM Tension t WHERE t.stanza.id = :stanzaId AND t.status = 'ACTIVE'")
    List<Tension> findActiveTensions(@Param("stanzaId") Long stanzaId);
    
    // Find high pressure tensions (7+)
    @Query("SELECT t FROM Tension t WHERE t.stanza.id = :stanzaId AND t.status = 'ACTIVE' AND t.pressure >= 7 ORDER BY t.pressure DESC")
    List<Tension> findHighPressureTensions(@Param("stanzaId") Long stanzaId);
    
    // Find tensions by pressure range
    @Query("SELECT t FROM Tension t WHERE t.stanza.id = :stanzaId AND t.status = 'ACTIVE' AND t.pressure >= :minPressure AND t.pressure <= :maxPressure ORDER BY t.pressure DESC")
    List<Tension> findByPressureRange(@Param("stanzaId") Long stanzaId, @Param("minPressure") int minPressure, @Param("maxPressure") int maxPressure);
    
    // Find tensions involving a character
    @Query("SELECT t FROM Tension t WHERE t.stanza.id = :stanzaId AND t.involvedCharacters LIKE CONCAT('%', :characterName, '%')")
    List<Tension> findInvolvingCharacter(@Param("stanzaId") Long stanzaId, @Param("characterName") String characterName);
    
    // Find tensions by source
    @Query("SELECT t FROM Tension t WHERE t.stanza.id = :stanzaId AND t.source = :source")
    List<Tension> findBySource(@Param("stanzaId") Long stanzaId, @Param("source") String source);
    
    // Find resolved tensions
    @Query("SELECT t FROM Tension t WHERE t.stanza.id = :stanzaId AND t.status = 'RESOLVED'")
    List<Tension> findResolvedTensions(@Param("stanzaId") Long stanzaId);
    
    // Find dormant tensions
    @Query("SELECT t FROM Tension t WHERE t.stanza.id = :stanzaId AND t.status = 'DORMANT'")
    List<Tension> findDormantTensions(@Param("stanzaId") Long stanzaId);
    
    // Count active tensions
    @Query("SELECT COUNT(t) FROM Tension t WHERE t.stanza.id = :stanzaId AND t.status = 'ACTIVE'")
    long countActiveTensions(@Param("stanzaId") Long stanzaId);
    
    // Find highest pressure tension
    @Query("SELECT t FROM Tension t WHERE t.stanza.id = :stanzaId AND t.status = 'ACTIVE' ORDER BY t.pressure DESC LIMIT 1")
    Tension findHighestPressureTension(@Param("stanzaId") Long stanzaId);
}
