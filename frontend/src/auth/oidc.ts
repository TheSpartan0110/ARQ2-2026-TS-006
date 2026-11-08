import { UserManager, WebStorageStateStore } from 'oidc-client-ts'

/**
 * OIDC client for the EatsNow Identity provider (ADR 005): authorization-code flow with PKCE as the public client
 * `eatsnow-web`. The issuer is the API Gateway. Tokens live in sessionStorage, so they end with the browser tab.
 *
 * Spring Authorization Server issues no refresh token to public clients. When the access token expires the app
 * redirects to Identity again; while the Identity login session is alive this returns at once without a password.
 */
export const OIDC_AUTHORITY: string = import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8090'

export const userManager = new UserManager({
  authority: OIDC_AUTHORITY,
  client_id: 'eatsnow-web',
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  response_type: 'code',
  scope: 'openid profile',
  loadUserInfo: false,
  automaticSilentRenew: false,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
})

/** Customer self-registration page on Identity (Spec 3.1). */
export const REGISTRATION_URL = `${OIDC_AUTHORITY}/register`
