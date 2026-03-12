package org.example.mercenary.domain.match.repository;

import com.fasterxml.jackson.databind.deser.DataFormatReaders;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;


public interface MatchRepository extends JpaRepository<MatchEntity, Long> {

}