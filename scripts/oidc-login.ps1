<#
.SYNOPSIS
  Obtains EatsNow access tokens for scripts through the real OIDC flow (ADR 005): authorization code with PKCE,
  logging in on the Identity login page exactly as a browser would. Dot-source it: . "$PSScriptRoot/oidc-login.ps1"

  Uses the confidential client `eatsnow-cli`, which also receives a rotating refresh token. The redirect URI is a
  loopback address that is never contacted: the script reads the authorization code from the redirect itself.
#>
Add-Type -AssemblyName System.Net.Http

function ConvertTo-Base64Url([byte[]]$bytes) {
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Get-EatsNowToken {
    param(
        [Parameter(Mandatory)][string]$Gateway,
        [Parameter(Mandatory)][string]$Email,
        [Parameter(Mandatory)][string]$Password,
        [string]$ClientId = "eatsnow-cli",
        [string]$ClientSecret = "dev-cli-secret",            # DEV ONLY, matches CLI_CLIENT_SECRET in docker-compose
        [string]$RedirectUri = "http://127.0.0.1:8765/callback"
    )
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $false                     # each redirect is inspected
    $handler.CookieContainer = New-Object System.Net.CookieContainer
    $http = New-Object System.Net.Http.HttpClient($handler)
    try {
        $next = {
            param($response)
            $location = $response.Headers.Location
            if ($null -eq $location) { throw "Expected a redirect, got HTTP $([int]$response.StatusCode)" }
            if ($location.IsAbsoluteUri) { return $location.AbsoluteUri }
            return (New-Object Uri((New-Object Uri($Gateway)), $location.OriginalString)).AbsoluteUri
        }

        # PKCE (RFC 7636) and state
        $random = New-Object byte[] 32
        [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($random)
        $verifier = ConvertTo-Base64Url $random
        $challenge = ConvertTo-Base64Url ([Security.Cryptography.SHA256]::Create().ComputeHash(
                [Text.Encoding]::ASCII.GetBytes($verifier)))
        $state = [guid]::NewGuid().ToString("N")
        $authorize = "$Gateway/oauth2/authorize?response_type=code&client_id=$ClientId&scope=openid" +
                "&redirect_uri=$([uri]::EscapeDataString($RedirectUri))&code_challenge=$challenge" +
                "&code_challenge_method=S256&state=$state"

        # 1. Authorization request: not logged in yet, so Identity redirects to its login page.
        $loginUrl = & $next ($http.GetAsync($authorize).Result)

        # 2. Login page: read the CSRF token of the form.
        $page = $http.GetAsync($loginUrl).Result.Content.ReadAsStringAsync().Result
        $csrf = [regex]::Match($page, 'name="_csrf"\s+value="([^"]+)"').Groups[1].Value
        if (-not $csrf) { throw "The Identity login page has no CSRF token" }

        # 3. Submit the credentials; Identity resumes the saved authorization request.
        $form = New-Object 'System.Collections.Generic.Dictionary[string,string]'
        $form["username"] = $Email; $form["password"] = $Password; $form["_csrf"] = $csrf
        $resumeUrl = & $next ($http.PostAsync($loginUrl, (New-Object System.Net.Http.FormUrlEncodedContent($form))).Result)
        if ($resumeUrl -match "/login\?error") { throw "Login failed for $Email (wrong password or inactive account)" }

        # 4. The resumed authorization request redirects to the client with the code.
        $callback = & $next ($http.GetAsync($resumeUrl).Result)
        $query = ([uri]$callback).Query
        $code = [regex]::Match($query, '[?&]code=([^&]+)').Groups[1].Value
        $returnedState = [regex]::Match($query, '[?&]state=([^&]+)').Groups[1].Value
        if (-not $code -or $returnedState -ne $state) { throw "No authorization code in the redirect: $callback" }

        # 5. Token request with client authentication and the PKCE verifier.
        $tokenForm = New-Object 'System.Collections.Generic.Dictionary[string,string]'
        $tokenForm["grant_type"] = "authorization_code"; $tokenForm["code"] = [uri]::UnescapeDataString($code)
        $tokenForm["redirect_uri"] = $RedirectUri; $tokenForm["code_verifier"] = $verifier
        $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, "$Gateway/oauth2/token")
        $basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${ClientId}:${ClientSecret}"))
        $request.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Basic", $basic)
        $request.Content = New-Object System.Net.Http.FormUrlEncodedContent($tokenForm)
        $response = $http.SendAsync($request).Result
        $body = $response.Content.ReadAsStringAsync().Result
        if (-not $response.IsSuccessStatusCode) { throw "Token request failed: HTTP $([int]$response.StatusCode) $body" }
        return $body | ConvertFrom-Json                     # access_token, refresh_token, id_token, expires_in
    } finally {
        $http.Dispose()
    }
}
