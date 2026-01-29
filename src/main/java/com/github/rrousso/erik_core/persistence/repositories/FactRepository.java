package com.github.rrousso.erik_core.persistence.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.rrousso.erik_core.persistence.entities.Fact;

@Repository
public interface FactRepository extends JpaRepository<Fact, Long> {
    
    // Find all facts for a stanza
    List<Fact> findByStanzaId(Long stanzaId);
    
    // Find by fact key
    @Query("SELECT f FROM Fact f WHERE f.stanza.id = :stanzaId AND f.factKey = :factKey")
    Optional<Fact> findByFactKey(@Param("stanzaId") Long stanzaId, @Param("factKey") String factKey);
    
    // Find by kind
    @Query("SELECT f FROM Fact f WHERE f.stanza.id = :stanzaId AND f.kind = :kind")
    List<Fact> findByKind(@Param("stanzaId") Long stanzaId, @Param("kind") String kind);
    
    // Find user private facts
    @Query("SELECT f FROM Fact f WHERE f.stanza.id = :stanzaId AND f.kind = 'USER_PRIVATE'")
    List<Fact> findUserPrivateFacts(@Param("stanzaId") Long stanzaId);
    
    // Find user public facts
    @Query("SELECT f FROM Fact f WHERE f.stanza.id = :stanzaId AND f.kind = 'USER_PUBLIC'")
    List<Fact> findUserPublicFacts(@Param("stanzaId") Long stanzaId);
    
    // Find event facts
    @Query("SELECT f FROM Fact f WHERE f.stanza.id = :stanzaId AND f.kind = 'EVENT' ORDER BY f.createdBeat, f.createdExchange")
    List<Fact> findEventFacts(@Param("stanzaId") Long stanzaId);
    
    // Find facts about a specific subject
    @Query("SELECT f FROM Fact f WHERE f.stanza.id = :stanzaId AND f.subjectType = :subjectType AND f.subjectId = :subjectId")
    List<Fact> findBySubject(@Param("stanzaId") Long stanzaId, @Param("subjectType") String subjectType, @Param("subjectId") String subjectId);
    
    // Find facts about user
    @Query("SELECT f FROM Fact f WHERE f.stanza.id = :stanzaId AND f.subjectType = 'user'")
    List<Fact> findUserFacts(@Param("stanzaId") Long stanzaId);
    
    // Find facts about a character
    @Query("SELECT f FROM Fact f WHERE f.stanza.id = :stanzaId AND f.subjectType = 'character' AND f.subjectId = :characterName")
    List<Fact> findCharacterFacts(@Param("stanzaId") Long stanzaId, @Param("characterName") String characterName);
    
    // Find facts created in a specific beat
    @Query("SELECT f FROM Fact f WHERE f.stanza.id = :stanzaId AND f.createdBeat = :beat")
    List<Fact> findByBeat(@Param("stanzaId") Long stanzaId, @Param("beat") Integer beat);
    
    // Check if fact key exists
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Fact f WHERE f.stanza.id = :stanzaId AND f.factKey = :factKey")
    boolean existsByFactKey(@Param("stanzaId") Long stanzaId, @Param("factKey") String factKey);
}
