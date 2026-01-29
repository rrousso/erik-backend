package com.github.rrousso.erik_core.persistence.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.rrousso.erik_core.persistence.entities.CharacterKnowledge;

@Repository
public interface CharacterKnowledgeRepository extends JpaRepository<CharacterKnowledge, Long> {
    
    // Find all knowledge for a character
    List<CharacterKnowledge> findByCharacterId(Long characterId);
    
    // Find specific fact knowledge for a character
    @Query("SELECT k FROM CharacterKnowledge k WHERE k.character.id = :characterId AND k.fact.id = :factId")
    Optional<CharacterKnowledge> findByCharacterAndFact(@Param("characterId") Long characterId, @Param("factId") Long factId);
    
    // Find by fact key
    @Query("SELECT k FROM CharacterKnowledge k WHERE k.character.id = :characterId AND k.fact.factKey = :factKey")
    Optional<CharacterKnowledge> findByCharacterAndFactKey(@Param("characterId") Long characterId, @Param("factKey") String factKey);
    
    // Check if character knows a fact
    @Query("SELECT CASE WHEN COUNT(k) > 0 THEN true ELSE false END FROM CharacterKnowledge k WHERE k.character.id = :characterId AND k.fact.id = :factId")
    boolean characterKnowsFact(@Param("characterId") Long characterId, @Param("factId") Long factId);
    
    // Find all who know a specific fact
    @Query("SELECT k FROM CharacterKnowledge k WHERE k.fact.id = :factId")
    List<CharacterKnowledge> findAllWhoKnowFact(@Param("factId") Long factId);
    
    // Find knowledge learned in a specific beat
    @Query("SELECT k FROM CharacterKnowledge k WHERE k.character.id = :characterId AND k.learnedBeat = :beat")
    List<CharacterKnowledge> findLearnedInBeat(@Param("characterId") Long characterId, @Param("beat") Integer beat);
    
    // Find knowledge by how it was learned
    @Query("SELECT k FROM CharacterKnowledge k WHERE k.character.id = :characterId AND k.how = :how")
    List<CharacterKnowledge> findByHowLearned(@Param("characterId") Long characterId, @Param("how") String how);
    
    // Find believed (not confirmed) knowledge
    @Query("SELECT k FROM CharacterKnowledge k WHERE k.character.id = :characterId AND k.status = 'BELIEVED'")
    List<CharacterKnowledge> findBelievedKnowledge(@Param("characterId") Long characterId);
    
    // Count facts a character knows
    @Query("SELECT COUNT(k) FROM CharacterKnowledge k WHERE k.character.id = :characterId")
    long countKnownFacts(@Param("characterId") Long characterId);
}
