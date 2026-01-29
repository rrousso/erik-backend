package com.github.rrousso.erik_core.persistence.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.rrousso.erik_core.entities.StanzaRecord;

@Repository
public interface StanzaRecordRepository extends JpaRepository<StanzaRecord, Long> {
	
	List<StanzaRecord> findByPersonaId(Long personaId);
    
    // Global full-text search (uses the search_vector + GIN index)
    @Query(value = "SELECT * FROM stanza_records WHERE search_vector @@ to_tsquery('english', :query)", 
           nativeQuery = true)
    List<StanzaRecord> fullTextSearch(@Param("query") String query);
    
    // Field-specific searches (ILIKE for case-insensitive partial match)
    @Query("SELECT s FROM StanzaRecord s WHERE LOWER(s.setting) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<StanzaRecord> searchBySetting(@Param("keyword") String keyword);
    
    @Query("SELECT s FROM StanzaRecord s WHERE LOWER(s.premise) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<StanzaRecord> searchByPremise(@Param("keyword") String keyword);
    
    @Query("SELECT s FROM StanzaRecord s WHERE LOWER(s.tone) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<StanzaRecord> searchByTone(@Param("keyword") String keyword);
    
    // Character search (needs to join the element collection)
    @Query("SELECT DISTINCT s FROM StanzaRecord s JOIN s.characters c WHERE LOWER(c) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<StanzaRecord> searchByCharacter(@Param("keyword") String keyword);
}