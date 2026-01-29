package com.github.rrousso.erik_core.persistence.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.rrousso.erik_core.persistence.entities.StanzaCharacter;

@Repository
public interface StanzaCharacterRepository extends JpaRepository<StanzaCharacter, Long> {
    
    // Find all characters for a stanza
    List<StanzaCharacter> findByStanzaId(Long stanzaId);
    
    // Find user character for a stanza
    @Query("SELECT c FROM StanzaCharacter c WHERE c.stanza.id = :stanzaId AND c.isUser = true")
    Optional<StanzaCharacter> findUserCharacter(@Param("stanzaId") Long stanzaId);
    
    // Find by presence status
    @Query("SELECT c FROM StanzaCharacter c WHERE c.stanza.id = :stanzaId AND c.presenceStatus = :status")
    List<StanzaCharacter> findByPresenceStatus(@Param("stanzaId") Long stanzaId, @Param("status") String status);
    
    // Find present characters (in scene now)
    @Query("SELECT c FROM StanzaCharacter c WHERE c.stanza.id = :stanzaId AND c.presenceStatus = 'present'")
    List<StanzaCharacter> findPresentCharacters(@Param("stanzaId") Long stanzaId);
    
    // Find potential characters (could appear)
    @Query("SELECT c FROM StanzaCharacter c WHERE c.stanza.id = :stanzaId AND c.presenceStatus = 'potential'")
    List<StanzaCharacter> findPotentialCharacters(@Param("stanzaId") Long stanzaId);
    
    // Find background characters
    @Query("SELECT c FROM StanzaCharacter c WHERE c.stanza.id = :stanzaId AND c.presenceStatus = 'background'")
    List<StanzaCharacter> findBackgroundCharacters(@Param("stanzaId") Long stanzaId);
    
    // Find character by name (case-insensitive)
    @Query("SELECT c FROM StanzaCharacter c WHERE c.stanza.id = :stanzaId AND LOWER(c.name) = LOWER(:name)")
    Optional<StanzaCharacter> findByName(@Param("stanzaId") Long stanzaId, @Param("name") String name);
    
    // Find non-user characters
    @Query("SELECT c FROM StanzaCharacter c WHERE c.stanza.id = :stanzaId AND c.isUser = false")
    List<StanzaCharacter> findNonUserCharacters(@Param("stanzaId") Long stanzaId);
    
    // Search by name pattern
    @Query("SELECT c FROM StanzaCharacter c WHERE c.stanza.id = :stanzaId AND LOWER(c.name) LIKE LOWER(CONCAT('%', :pattern, '%'))")
    List<StanzaCharacter> searchByName(@Param("stanzaId") Long stanzaId, @Param("pattern") String pattern);
}
