package com.github.rrousso.erik_core.persistence.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.rrousso.erik_core.persistence.entities.StanzaEvent;

@Repository
public interface StanzaEventRepository extends JpaRepository<StanzaEvent, Long> {
    
    // Find all events for a stanza (chronological)
    @Query("SELECT e FROM StanzaEvent e WHERE e.stanza.id = :stanzaId ORDER BY e.beatNumber, e.exchangeNumber, e.createdAt")
    List<StanzaEvent> findByStanzaIdOrdered(@Param("stanzaId") Long stanzaId);
    
    // Find events in a specific beat
    @Query("SELECT e FROM StanzaEvent e WHERE e.stanza.id = :stanzaId AND e.beatNumber = :beat ORDER BY e.exchangeNumber, e.createdAt")
    List<StanzaEvent> findByBeat(@Param("stanzaId") Long stanzaId, @Param("beat") Integer beat);
    
    // Find major events only
    @Query("SELECT e FROM StanzaEvent e WHERE e.stanza.id = :stanzaId AND e.isMajor = true ORDER BY e.beatNumber, e.exchangeNumber")
    List<StanzaEvent> findMajorEvents(@Param("stanzaId") Long stanzaId);
    
    // Find events involving a character
    @Query("SELECT e FROM StanzaEvent e WHERE e.stanza.id = :stanzaId AND e.involvedCharacters LIKE CONCAT('%', :characterName, '%') ORDER BY e.beatNumber, e.exchangeNumber")
    List<StanzaEvent> findInvolvingCharacter(@Param("stanzaId") Long stanzaId, @Param("characterName") String characterName);
    
    // Find recent events (last N)
    @Query("SELECT e FROM StanzaEvent e WHERE e.stanza.id = :stanzaId ORDER BY e.beatNumber DESC, e.exchangeNumber DESC, e.createdAt DESC LIMIT :limit")
    List<StanzaEvent> findRecentEvents(@Param("stanzaId") Long stanzaId, @Param("limit") int limit);
    
    // Count events in stanza
    @Query("SELECT COUNT(e) FROM StanzaEvent e WHERE e.stanza.id = :stanzaId")
    long countEvents(@Param("stanzaId") Long stanzaId);
    
    // Count major events
    @Query("SELECT COUNT(e) FROM StanzaEvent e WHERE e.stanza.id = :stanzaId AND e.isMajor = true")
    long countMajorEvents(@Param("stanzaId") Long stanzaId);
    
    // Search events by description
    @Query("SELECT e FROM StanzaEvent e WHERE e.stanza.id = :stanzaId AND LOWER(e.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY e.beatNumber, e.exchangeNumber")
    List<StanzaEvent> searchByDescription(@Param("stanzaId") Long stanzaId, @Param("keyword") String keyword);
}
