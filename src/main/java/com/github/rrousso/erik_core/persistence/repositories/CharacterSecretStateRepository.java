package com.github.rrousso.erik_core.persistence.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.rrousso.erik_core.persistence.entities.CharacterSecretState;

@Repository
public interface CharacterSecretStateRepository extends JpaRepository<CharacterSecretState, Long> {
    
    // Find all secret states for a character
    List<CharacterSecretState> findByCharacterId(Long characterId);
    
    // Find specific secret state for a character
    @Query("SELECT s FROM CharacterSecretState s WHERE s.character.id = :characterId AND s.secret.id = :secretId")
    Optional<CharacterSecretState> findByCharacterAndSecret(@Param("characterId") Long characterId, @Param("secretId") Long secretId);
    
    // Find by fact key (through secret -> fact relationship)
    @Query("SELECT s FROM CharacterSecretState s WHERE s.character.id = :characterId AND s.secret.fact.factKey = :factKey")
    Optional<CharacterSecretState> findByCharacterAndFactKey(@Param("characterId") Long characterId, @Param("factKey") String factKey);
    
    // Check if character has access to a secret (state = KNOWS)
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM CharacterSecretState s WHERE s.character.id = :characterId AND s.secret.id = :secretId AND s.state = 'KNOWS'")
    boolean hasSecretAccess(@Param("characterId") Long characterId, @Param("secretId") Long secretId);
    
    // Find all characters who know a secret
    @Query("SELECT s FROM CharacterSecretState s WHERE s.secret.id = :secretId AND s.state = 'KNOWS'")
    List<CharacterSecretState> findAllWhoKnowSecret(@Param("secretId") Long secretId);
    
    // Find all characters suspicious of a secret
    @Query("SELECT s FROM CharacterSecretState s WHERE s.secret.id = :secretId AND s.state = 'SUSPICIOUS'")
    List<CharacterSecretState> findAllSuspiciousOfSecret(@Param("secretId") Long secretId);
    
    // Find secrets a character knows
    @Query("SELECT s FROM CharacterSecretState s WHERE s.character.id = :characterId AND s.state = 'KNOWS'")
    List<CharacterSecretState> findKnownSecrets(@Param("characterId") Long characterId);
    
    // Find secrets a character is suspicious about
    @Query("SELECT s FROM CharacterSecretState s WHERE s.character.id = :characterId AND s.state = 'SUSPICIOUS'")
    List<CharacterSecretState> findSuspiciousSecrets(@Param("characterId") Long characterId);
    
    // Find secrets a character is unaware of
    @Query("SELECT s FROM CharacterSecretState s WHERE s.character.id = :characterId AND s.state = 'UNAWARE'")
    List<CharacterSecretState> findUnawareSecrets(@Param("characterId") Long characterId);
    
    // Count secrets a character knows
    @Query("SELECT COUNT(s) FROM CharacterSecretState s WHERE s.character.id = :characterId AND s.state = 'KNOWS'")
    long countKnownSecrets(@Param("characterId") Long characterId);
}
