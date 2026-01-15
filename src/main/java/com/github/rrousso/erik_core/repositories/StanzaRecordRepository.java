package com.github.rrousso.erik_core.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.github.rrousso.erik_core.entities.StanzaRecord;

@Repository
public interface StanzaRecordRepository extends JpaRepository<StanzaRecord, Long> {
	
	List<StanzaRecord> findByPersonaId(Long personaId);
}