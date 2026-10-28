package com.eatsnow.catalog.application.service;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.IdempotencyConflictException;
import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.common.domain.exception.ItemUnavailableException;
import com.eatsnow.common.domain.exception.RequestInProgressException;
import com.eatsnow.common.domain.model.IdempotencyEntry;
import com.eatsnow.catalog.domain.model.MenuItem;
import com.eatsnow.common.domain.model.Money;
import com.eatsnow.catalog.domain.model.OrderItem;
import com.eatsnow.catalog.domain.model.Quote;
import com.eatsnow.catalog.domain.model.RequestedItem;
import com.eatsnow.catalog.domain.model.Restaurant;
import com.eatsnow.catalog.domain.port.in.CreateQuoteUseCase;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.catalog.domain.port.out.MenuItemRepositoryPort;
import com.eatsnow.catalog.domain.port.out.QuoteSignerPort;
import com.eatsnow.catalog.domain.port.out.RestaurantRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quote application service (Spec 2.3 and 3.3).
 * Quotations are created directly against PostgreSQL (not Redis) to guarantee price integrity.
 * Quotes are signed with an asymmetric key for verification by Order Service.
 */
@Service
public class QuoteService implements CreateQuoteUseCase {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);
    private static final int QUOTE_EXPIRY_SECONDS = 120; // Spec 2.3

    private final RestaurantRepositoryPort restaurantRepository;
    private final MenuItemRepositoryPort menuItemRepository;
    private final QuoteSignerPort quoteSigner;
    private final IdempotencyStoragePort idempotencyStorage;

    public QuoteService(
            RestaurantRepositoryPort restaurantRepository,
            MenuItemRepositoryPort menuItemRepository,
            QuoteSignerPort quoteSigner,
            IdempotencyStoragePort idempotencyStorage
    ) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.quoteSigner = quoteSigner;
        this.idempotencyStorage = idempotencyStorage;
    }

    @Override
    @Transactional
    public Quote createQuote(
            UUID customerId,
            UUID restaurantId,
            List<RequestedItem> items,
            String idempotencyKey,
            String rawPayload
    ) {
        Objects.requireNonNull(customerId, "customerId cannot be null");
        Objects.requireNonNull(restaurantId, "restaurantId cannot be null");
        Objects.requireNonNull(items, "items cannot be null");

        if (items.isEmpty() || items.size() > 50) {
            throw new InvalidRequestException("items must contain between 1 and 50 entries");
        }

        // Validate duplicate menuItemId (Spec 2.2)
        Set<UUID> seenItemIds = new HashSet<>();
        for (RequestedItem item : items) {
            if (!seenItemIds.add(item.menuItemId())) {
                throw new InvalidRequestException("Duplicate menuItemId is invalid: " + item.menuItemId());
            }
        }

        String requestHash = computeSha256(rawPayload);
        String scopeKey = customerId + ":POST:/api/v1/quotes:" + (idempotencyKey != null ? idempotencyKey : UUID.randomUUID());

        if (idempotencyKey != null) {
            Optional<IdempotencyEntry> existing = idempotencyStorage.find(scopeKey);
            if (existing.isPresent()) {
                IdempotencyEntry entry = existing.get();
                if (entry.isInProgress()) {
                    throw new RequestInProgressException("Request is currently in progress for idempotency key: " + idempotencyKey);
                }
                if (!entry.requestHash().equals(requestHash)) {
                    throw new IdempotencyConflictException("Payload does not match original request for key: " + idempotencyKey);
                }
                // Return original completed response if already processed
            }
            idempotencyStorage.start(scopeKey, requestHash);
        }

        try {
            // Verify restaurant in PostgreSQL
            Restaurant restaurant = restaurantRepository.findById(restaurantId)
                    .orElseThrow(() -> new EntityNotFoundException("Restaurant not found: " + restaurantId));

            if (!restaurant.available()) {
                throw new ItemUnavailableException("Restaurant is currently unavailable: " + restaurantId);
            }

            // Verify and retrieve items in PostgreSQL (Spec 2.3: current PostgreSQL values, not unchecked Redis)
            List<UUID> requestedIds = items.stream().map(RequestedItem::menuItemId).toList();
            List<MenuItem> foundMenuItems = menuItemRepository.findByIds(requestedIds);

            Map<UUID, MenuItem> menuItemMap = foundMenuItems.stream()
                    .collect(Collectors.toMap(MenuItem::id, Function.identity()));

            List<OrderItem> orderItems = new ArrayList<>();
            Money total = Money.cop(0);

            for (RequestedItem req : items) {
                MenuItem menuItem = menuItemMap.get(req.menuItemId());
                if (menuItem == null) {
                    throw new EntityNotFoundException("Menu item not found: " + req.menuItemId());
                }
                if (!menuItem.restaurantId().equals(restaurantId)) {
                    throw new InvalidRequestException("Menu item does not belong to specified restaurant: " + req.menuItemId());
                }
                if (!menuItem.available()) {
                    throw new ItemUnavailableException("Menu item is unavailable: " + menuItem.name());
                }

                OrderItem orderItem = new OrderItem(
                        menuItem.id(),
                        menuItem.name(),
                        req.quantity(),
                        menuItem.price()
                );
                orderItems.add(orderItem);
                total = total.add(orderItem.subtotal());
            }

            UUID quoteId = UUID.randomUUID();
            Instant expiresAt = Instant.now().plusSeconds(QUOTE_EXPIRY_SECONDS);

            // Asymmetrically sign the quote token (Spec 2.3)
            String quoteToken = quoteSigner.signQuoteToken(
                    quoteId,
                    customerId,
                    restaurantId,
                    orderItems,
                    total,
                    expiresAt
            );

            Quote quote = new Quote(
                    quoteId,
                    customerId,
                    restaurantId,
                    orderItems,
                    total,
                    expiresAt,
                    quoteToken
            );

            if (idempotencyKey != null) {
                idempotencyStorage.complete(scopeKey, requestHash, 201, quote.toString());
            }

            return quote;

        } catch (Exception ex) {
            if (idempotencyKey != null) {
                idempotencyStorage.release(scopeKey);
            }
            throw ex;
        }
    }

    private String computeSha256(String data) {
        if (data == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
