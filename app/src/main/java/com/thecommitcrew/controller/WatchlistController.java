package com.thecommitcrew.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thecommitcrew.auth.CheckAuth;
import com.thecommitcrew.domain.dto.InstrumentResponseDTO;
import com.thecommitcrew.domain.dto.WatchlistRequestDTO;
import com.thecommitcrew.domain.exception.UnauthorizedAccountException;
import com.thecommitcrew.service.WatchlistService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/accounts/{id}/watchlist")
public class WatchlistController {

    private static final String ACCOUNT_ID_ATTRIBUTE = "accountId";
    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    @CheckAuth
    public ResponseEntity<List<InstrumentResponseDTO>> getWatchlist(
        @PathVariable("id") String accountId,
        HttpServletRequest request
    ) {
        verifyAccount(accountId, request);
        return ResponseEntity.ok(watchlistService.getWatchlist(accountId));
    }

    @PostMapping
    @CheckAuth
    public ResponseEntity<InstrumentResponseDTO> addToWatchlist(
        @PathVariable("id") String accountId,
        @Valid @RequestBody WatchlistRequestDTO body,
        HttpServletRequest request
    ) {
        verifyAccount(accountId, request);
        return ResponseEntity.ok(watchlistService.addToWatchlist(accountId, body.symbol()));
    }

    @DeleteMapping("/{symbol}")
    @CheckAuth
    public ResponseEntity<Void> removeFromWatchlist(
        @PathVariable("id") String accountId,
        @PathVariable String symbol,
        HttpServletRequest request
    ) {
        verifyAccount(accountId, request);
        watchlistService.removeFromWatchlist(accountId, symbol);
        return ResponseEntity.noContent().build();
    }

    private void verifyAccount(String accountId, HttpServletRequest request) {
        String userAccountId = (String) request.getAttribute(ACCOUNT_ID_ATTRIBUTE);
        if (!accountId.equals(userAccountId)) {
            throw new UnauthorizedAccountException(
                "Forbidden: You do not have access to account " + accountId
            );
        }
    }
}
