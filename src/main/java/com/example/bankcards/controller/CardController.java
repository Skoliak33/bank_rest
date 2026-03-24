package com.example.bankcards.controller;

import com.example.bankcards.dto.card.CardCreateRequest;
import com.example.bankcards.dto.card.CardResponse;
import com.example.bankcards.dto.card.TransferRequest;
import com.example.bankcards.security.CustomUserDetails;
import com.example.bankcards.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cards", description = "Card management and transfers")
public class CardController {

    private final CardService cardService;

    @GetMapping
    @Operation(summary = "Get cards (ADMIN: all, USER: own). Filter by status query param.")
    public Page<CardResponse> getCards(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @ParameterObject Pageable pageable) {
        return cardService.getCards(status, currentUser, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get card by ID (ADMIN or card owner)")
    public CardResponse getCardById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return cardService.getCardById(id, currentUser);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Create a new card (ADMIN only)")
    public CardResponse createCard(@Valid @RequestBody CardCreateRequest request) {
        return cardService.createCard(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Delete card (ADMIN only)")
    public void deleteCard(@PathVariable Long id) {
        cardService.deleteCard(id);
    }

    @PatchMapping("/{id}/block")
    @Operation(summary = "Block card (ADMIN: any card, USER: own card)")
    public CardResponse blockCard(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return cardService.blockCard(id, currentUser);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Activate card (ADMIN only)")
    public CardResponse activateCard(@PathVariable Long id) {
        return cardService.activateCard(id);
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer between own cards (USER)")
    public CardResponse transfer(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return cardService.transfer(request, currentUser);
    }
}
