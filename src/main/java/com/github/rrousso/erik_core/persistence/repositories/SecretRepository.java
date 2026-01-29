package com.github.rrousso.erik_core.persistence.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.rrousso.erik_core.persistence.entities.Secret;

@Repository
public interface SecretRepository extends JpaRepository<Secret, Long> {
    
    // Find all secrets for a stanza
    List<Secret> findByStanzaId(Long stanzaId);
    
    // Find secret by fact
    @Query("SELECT s FROM Secret s WHERE s.fact.id = :factId")
    Optional<Secret> findByFactId(@Param("factId") Long factId);
    
    // Find secret by fact key
    @Query("SELECT s FROM Secret s WHERE s.stanza.id = :stanzaId AND s.fact.factKey = :factKey")
    Optional<Secret> findByFactKey(@Param("stanzaId") Long stanzaId, @Param("factKey") String factKey);
    
    // Find all secrets that are told-only
    @Query("SELECT s FROM Secret s WHERE s.stanza.id = :stanzaId AND s.allowedRevealModes = 'TOLD'")
    List<Secret> findToldOnlySecrets(@Param("stanzaId") Long stanzaId);
    
    // Find all inferable secrets
    @Query("SELECT s FROM Secret s WHERE s.stanza.id = :stanzaId AND s.inferable = true")
    List<Secret> findInferableSecrets(@Param("stanzaId") Long stanzaId);
    
    // Check if a fact is protected by a secret
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Secret s WHERE s.fact.id = :factId")
    boolean isFactProtected(@Param("factId") Long factId);
}
