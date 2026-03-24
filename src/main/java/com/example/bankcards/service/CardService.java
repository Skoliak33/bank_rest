package com.example.bankcards.service;

import com.example.bankcards.dto.card.CardCreateRequest;
import com.example.bankcards.dto.card.CardResponse;
import com.example.bankcards.dto.card.TransferRequest;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.CardStatus;
import com.example.bankcards.entity.Role;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.CardOperationException;
import com.example.bankcards.exception.InsufficientFundsException;
import com.example.bankcards.exception.ResourceNotFoundException;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.security.CustomUserDetails;
import com.example.bankcards.util.CardNumberUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Бизнес-логика управления картами: CRUD, блокировка, активация, переводы.
 * Права доступа: ADMIN видит/управляет всеми картами, USER - только своими.
 * Переводы - строго между своими картами (для любой роли).
 */
@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final CardNumberUtil cardNumberUtil;

    /** Список карт с фильтрацией. ADMIN видит все, USER - только свои. */
    @Transactional(readOnly = true)
    public Page<CardResponse> getCards(String status, CustomUserDetails currentUser, Pageable pageable) {
        Specification<Card> spec = buildSpecification(status, currentUser);
        return cardRepository.findAll(spec, pageable).map(CardResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "cards", key = "#id")
    public CardResponse getCardById(Long id, CustomUserDetails currentUser) {
        Card card = findCardOrThrow(id);
        checkAccess(card, currentUser);
        return CardResponse.fromEntity(card);
    }

    /** Создание карты (только ADMIN). Генерирует номер, шифрует, маскирует. */
    @Transactional
    @CacheEvict(value = "cards", allEntries = true)
    public CardResponse createCard(CardCreateRequest request) {
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.ownerId()));

        String plainNumber = cardNumberUtil.generateCardNumber();

        LocalDate expiration = request.expirationDate() != null
                ? request.expirationDate()
                : LocalDate.now().plusYears(3);

        Card card = Card.builder()
                .encryptedNumber(cardNumberUtil.encrypt(plainNumber))
                .maskedNumber(cardNumberUtil.mask(plainNumber))
                .owner(owner)
                .expirationDate(expiration)
                .status(CardStatus.ACTIVE)
                .build();

        Card saved = cardRepository.save(card);
        return CardResponse.fromEntity(saved);
    }

    /** Блокировка карты. ADMIN - любую, USER - только свою. */
    @Transactional
    @CacheEvict(value = "cards", allEntries = true)
    public CardResponse blockCard(Long id, CustomUserDetails currentUser) {
        Card card = findCardOrThrow(id);

        // USER может заблокировать только свою карту
        if (!isAdmin(currentUser)) {
            checkOwnership(card, currentUser);
        }

        if (card.getStatus() == CardStatus.BLOCKED) {
            throw new CardOperationException("Card is already blocked");
        }

        card.setStatus(CardStatus.BLOCKED);
        return CardResponse.fromEntity(cardRepository.save(card));
    }

    /** Активация карты (только ADMIN). */
    @Transactional
    @CacheEvict(value = "cards", allEntries = true)
    public CardResponse activateCard(Long id) {
        Card card = findCardOrThrow(id);

        if (card.getStatus() == CardStatus.ACTIVE) {
            throw new CardOperationException("Card is already active");
        }

        card.setStatus(CardStatus.ACTIVE);
        return CardResponse.fromEntity(cardRepository.save(card));
    }

    @Transactional
    @CacheEvict(value = "cards", allEntries = true)
    public void deleteCard(Long id) {
        if (!cardRepository.existsById(id)) {
            throw new ResourceNotFoundException("Card not found: " + id);
        }
        cardRepository.deleteById(id);
    }

    /**
     * Перевод между своими картами. Проверки:
     * 1) Карты разные  2) Обе принадлежат текущему пользователю
     * 3) Обе активны   4) На источнике достаточно средств
     */
    @Transactional
    @CacheEvict(value = "cards", allEntries = true)
    public CardResponse transfer(TransferRequest request, CustomUserDetails currentUser) {
        if (request.sourceCardId().equals(request.targetCardId())) {
            throw new CardOperationException("Source and target cards must be different");
        }

        Card source = findCardOrThrow(request.sourceCardId());
        Card target = findCardOrThrow(request.targetCardId());

        // Обе карты должны принадлежать текущему пользователю (независимо от роли)
        if (!source.getOwner().getId().equals(currentUser.getUser().getId())) {
            throw new CardOperationException("Source card does not belong to you");
        }
        if (!target.getOwner().getId().equals(currentUser.getUser().getId())) {
            throw new CardOperationException("Target card does not belong to you");
        }

        // Обе карты должны быть активны
        if (source.getStatus() != CardStatus.ACTIVE) {
            throw new CardOperationException("Source card is not active");
        }
        if (target.getStatus() != CardStatus.ACTIVE) {
            throw new CardOperationException("Target card is not active");
        }

        // Проверка достаточности средств
        if (source.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException("Insufficient funds on source card");
        }

        source.setBalance(source.getBalance().subtract(request.amount()));
        target.setBalance(target.getBalance().add(request.amount()));

        cardRepository.save(source);
        cardRepository.save(target);

        return CardResponse.fromEntity(source);
    }

    private Card findCardOrThrow(Long id) {
        return cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + id));
    }

    private void checkAccess(Card card, CustomUserDetails currentUser) {
        if (!isAdmin(currentUser)) {
            checkOwnership(card, currentUser);
        }
    }

    private void checkOwnership(Card card, CustomUserDetails currentUser) {
        if (!card.getOwner().getId().equals(currentUser.getUser().getId())) {
            throw new AccessDeniedException("Access denied to this card");
        }
    }

    private boolean isAdmin(CustomUserDetails currentUser) {
        return currentUser.getUser().getRole() == Role.ROLE_ADMIN;
    }

    /** Динамическая спецификация для фильтрации карт (JPA Criteria API). */
    private Specification<Card> buildSpecification(String status, CustomUserDetails currentUser) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // USER видит только свои карты
            if (!isAdmin(currentUser)) {
                predicates.add(cb.equal(root.get("owner").get("id"), currentUser.getUser().getId()));
            }

            // Фильтр по статусу (опционально)
            if (status != null && !status.isBlank()) {
                try {
                    CardStatus cardStatus = CardStatus.valueOf(status.toUpperCase());
                    predicates.add(cb.equal(root.get("status"), cardStatus));
                } catch (IllegalArgumentException ignored) {
                    // Невалидное значение статуса - игнорируем фильтр
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
