package com.example.bankcards.repository;

import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.CardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий карт. JpaSpecificationExecutor - для динамической фильтрации
 * (по владельцу, статусу и т.д.) через Criteria API.
 */
public interface CardRepository extends JpaRepository<Card, Long>, JpaSpecificationExecutor<Card> {

    /** JOIN FETCH owner - решает N+1 при листинге карт */
    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Card> findAll(Specification<Card> spec, Pageable pageable);

    /** JOIN FETCH owner - решает N+1 при получении одной карты */
    @Override
    @EntityGraph(attributePaths = "owner")
    Optional<Card> findById(Long id);

    /** Поиск карт с заданным статусом и истёкшим сроком действия (для автоматической просрочки) */
    List<Card> findByStatusAndExpirationDateBefore(CardStatus status, LocalDate date);
}
