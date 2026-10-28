package com.eatsnow.catalog.infrastructure.adapter.out.crypto;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.catalog.domain.model.OrderItem;
import com.eatsnow.catalog.domain.port.out.QuoteSignerPort;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Asymmetric quote signer producing signed RS256 JWTs (Spec 2.3).
 * Signs: iss ("catalog"), aud ("orders"), exp, jti (quoteId), customerId, restaurantId, items, total.
 */
@Component
public class RsaJwtQuoteSigner implements QuoteSignerPort {

    private static final Logger log = LoggerFactory.getLogger(RsaJwtQuoteSigner.class);

    private static final String ISSUER = "catalog";
    private static final String AUDIENCE = "orders";
    private static final String KEY_ID = "eatsnow-catalog-key-1";

    private RSAKey rsaJwk;
    private JWSSigner signer;

    @PostConstruct
    public void init() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair keyPair = gen.generateKeyPair();

            this.rsaJwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(KEY_ID)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();

            this.signer = new RSASSASigner(rsaJwk);
            log.info("Initialized RSA quote signer with key ID: {}", KEY_ID);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize RSA key pair for quote signing", ex);
        }
    }

    @Override
    public String signQuoteToken(
            UUID quoteId,
            UUID customerId,
            UUID restaurantId,
            List<OrderItem> items,
            Money total,
            Instant expiresAt
    ) {
        try {
            List<Map<String, Object>> itemsPayload = items.stream().map(item -> {
                Map<String, Object> map = new HashMap<>();
                map.put("menuItemId", item.menuItemId().toString());
                map.put("name", item.name());
                map.put("quantity", item.quantity());
                Map<String, Object> priceMap = new HashMap<>();
                priceMap.put("amountMinor", item.unitPrice().amountMinor());
                priceMap.put("currency", item.unitPrice().currency());
                map.put("unitPrice", priceMap);
                return map;
            }).toList();

            Map<String, Object> totalPayload = new HashMap<>();
            totalPayload.put("amountMinor", total.amountMinor());
            totalPayload.put("currency", total.currency());

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .audience(AUDIENCE)
                    .jwtID(quoteId.toString())
                    .expirationTime(Date.from(expiresAt))
                    .issueTime(new Date())
                    .claim("customerId", customerId.toString())
                    .claim("restaurantId", restaurantId.toString())
                    .claim("items", itemsPayload)
                    .claim("total", totalPayload)
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(KEY_ID)
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claimsSet);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (Exception ex) {
            throw new RuntimeException("Error signing quote token", ex);
        }
    }

    @Override
    public String getPublicKeyJwksJson() {
        RSAKey publicJwk = rsaJwk.toPublicJWK();
        return new JWKSet(publicJwk).toJSONObject().toString();
    }
}
