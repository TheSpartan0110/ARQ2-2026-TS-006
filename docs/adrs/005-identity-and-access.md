# ADR 005 — Identity and Access with Spring Authorization Server

- **Status:** Proposed
- **Category:** Security

## Context and Problem

EatsNow must centrally manage profiles and access for customers, restaurants and drivers (Feature 1). Every request enters through the API Gateway, which must validate identity and roles before routing. Restaurant staff and drivers must only act on their own orders and deliveries, and services must authenticate each other for internal calls. Passwords must never be handled by individual business services.

## Considered Alternatives

1. **Custom JWT login in each service:** simple at first, but duplicates security code, spreads credential handling and makes token rotation and revocation hard.
2. **Keycloak as a separate identity provider:** complete open-source OIDC server, but adds a heavy extra container and a second user model that must be synchronized with EatsNow profiles.
3. **Spring Authorization Server inside the Identity & Access Service:** standards-based OAuth 2.1 / OIDC server embedded in the same Spring Boot service that owns profiles and roles.

## Decision

The Identity & Access Service embeds Spring Authorization Server and acts as the OIDC provider for EatsNow, as the single container shown in the C4 diagram. It stores credentials, profiles and roles (`USER`, `RESTAURANT`, `DRIVER`, `ADMIN`) in the `identity` schema and issues signed JWT access tokens.

- Customer, restaurant and driver apps log in with the authorization-code flow with PKCE.
- Access tokens carry the subject, roles and, when applicable, `restaurantId` or `driverId` claims. They expire after 15 minutes; refresh tokens rotate on use.
- The API Gateway and every service validate signature, issuer, audience and expiry using the published JWKS keys.
- Services use the client-credentials flow for internal calls.
- Customers self-register as `USER`. Restaurant, driver and admin accounts are created only by an `ADMIN`; no public endpoint can grant roles.

## Trade-offs

### Positives (What We Gain)

- One place handles credentials, login and roles.
- Standard OIDC and JWT tokens are validated locally by the Gateway and services, without a synchronous call per request.
- No extra container; it fits the Spring Boot stack and the C4 diagram.

### Negatives (Risks / What We Lose)

- The team must configure and maintain authorization-server features (key rotation, consent, client registration) that Keycloak offers out of the box.
- If the Identity service is down, new logins and token refreshes fail, although already issued tokens remain valid until expiry.
- Revoked access remains usable until the short-lived access token expires.

These risks will be mitigated through short token lifetimes, key rotation with overlapping JWKS keys, health checks and alerts on the Identity service, and the option to replace it with Keycloak without changing the token contract.

## Final Justification

Spring Authorization Server gives EatsNow standard, centrally managed authentication and role-based access with the least additional infrastructure. Because clients and services depend only on OIDC and JWT standards, the provider can be replaced later without changing the contracts in `docs/spec.md`.
