# EatsNow — Web Frontend (React + React Aria + Vite)

Frontend client application for the EatsNow platform, built with **React**, **React Aria Components**, **React Router**, and **TypeScript**.

---

## 1. Design System & Global Component Wrappers (`Archi*`)

In accordance with platform conventions, UI primitives wrap **React Aria Components** with custom styling, accessibility attributes, and loading states under the `Archi*` design system prefix (`src/components/ui/`):

| Component Wrapper | Underlying React Aria Primitive | Description |
|---|---|---|
| [`ArchiButton`](src/components/ui/ArchiButton.tsx) | `Button` | Accessible button with variants (`primary`, `secondary`, `outline`, `destructive`, `ghost`), sizes, and loading state. |
| [`ArchiTextField`](src/components/ui/ArchiTextField.tsx) | `TextField`, `Label`, `Input`, `FieldError`, `Text` | Accessible form text input with floating labels and error formatting. |
| [`ArchiCard`](src/components/ui/ArchiCard.tsx) | `div` (Semantic card container) | Card with header, title, description, and content sections. |
| [`ArchiBadge`](src/components/ui/ArchiBadge.tsx) | `span` (Status pill) | Role badges (`USER`, `ADMIN`, `RESTAURANT`, `DRIVER`) with colored themes. |
| [`ArchiAlert`](src/components/ui/ArchiAlert.tsx) | `div` (Feedback banner) | Accessible alert banner with status icons (`error`, `success`, `info`). |
| [`ArchiHeader`](src/components/ui/ArchiHeader.tsx) | Navigation bar | Sticky top navigation bar showing branding, active roles, and logout. |

---

## 2. Authentication Flow

- **Login (OIDC, ADR 005):** "Sign in" redirects to the Identity login page through the API Gateway (`http://localhost:8090`, the issuer). The app is the public client `eatsnow-web` and uses the authorization-code flow with PKCE ([`oidc-client-ts`](src/auth/oidc.ts)); `/callback` exchanges the code for an RS256 access token valid for 15 minutes. The app never sees the password.
- **Renewal:** public clients receive no refresh token. When the access token expires, the app redirects to Identity again; while the Identity login session is alive this returns without asking for the password.
- **Registration:** "Create an account" opens the Identity registration page (`/register`); new accounts get the `USER` role only.
- **Logout:** ends the Identity session as well (OIDC RP-initiated logout) and returns to the app.
- **Profile (`GET /api/v1/profiles/me`):** Retrieves the authenticated user's profile with `Authorization: Bearer <token>`.
- **Display Name Update (`PATCH /api/v1/profiles/me`):** Updates `displayName` enforcing `Idempotency-Key: UUID`.
- **JWKS Inspector (`GET /oauth2/jwks`):** Shows published public RSA keys used by the Gateway and microservices for offline token validation.

API calls go to `/api` on the dev server, which proxies them to the API Gateway.

---

## 3. Development Setup

```bash
# Navigate to the frontend directory
cd frontend

# Install dependencies
pnpm install

# Start development server (runs on http://localhost:5173, the registered OIDC redirect origin;
# the backend must be up: docker compose up -d)
pnpm dev

# Build for production with TypeScript checks
pnpm build
```

---

## 4. Test Accounts (Pre-seeded in PostgreSQL)

| Role | Email | Password | Purpose |
|---|---|---|---|
| `USER` | `alex@example.test` | `password123` | Customer profile |
| `ADMIN` | `admin@eatsnow.test` | `password123` | Administrator profile |
| `DRIVER` | `sam@example.test` | `password123` | Delivery driver profile |
