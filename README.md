# teslaHitch

A self-hosted service that generates the certificates needed to access Tesla's Fleet API and proxies vehicle commands. Designed to run alongside [tesla/vehicle-command](https://github.com/teslamotors/vehicle-command) and integrate with Home Assistant via [teslahacs](https://github.com/martinsaul/teslahacs).

## What it does

1. **Generates an EC key pair** (prime256v1) on first startup — this is the "fleet key" Tesla requires for vehicle commands
2. **Generates a self-signed TLS certificate** for the Tesla HTTP proxy and Spring Boot HTTPS
3. **Serves your public key** at `/.well-known/appspecific/com.tesla.3p.public-key.pem` so you can register it with Tesla
4. **Handles the full OAuth 2.0 flow** with Tesla's auth servers (token exchange + refresh)
5. **Manages token lifecycle** — proactively refreshes access tokens every 2 hours so they're always valid
6. **Proxies vehicle commands** through the Tesla HTTP proxy to your car
7. **Provides a config endpoint** (`/api/ha/config`) for automatic Home Assistant integration setup

## Token Management

teslaHitch is the **sole token authority**. It owns the OAuth session with Tesla and is the only service that refreshes tokens. This is critical because Tesla uses **refresh token rotation** — every refresh invalidates the previous refresh token. If multiple services refresh independently, they invalidate each other's tokens and auth is lost.

How it works:

- A **scheduled job** force-refreshes the access token every 2 hours. Since Tesla access tokens last ~8 hours, tokens served to consumers always have ~8 hours of remaining life.
- All refresh operations are **thread-safe** (guarded by a `ReentrantLock`) to prevent concurrent refreshes from causing token rotation conflicts.
- The `/api/ha/config` endpoint always returns a **currently-valid access token** with correct expiration timestamps.
- teslahacs is designed to re-fetch tokens from this endpoint before they expire, rather than refreshing directly with Tesla.

## Prerequisites

- Docker and Docker Compose
- A Tesla developer account at [developer.tesla.com](https://developer.tesla.com)
- A registered application with a Client ID, Client Secret, and Redirect URI
- A Traefik reverse proxy with an external `traefik` Docker network

## Setup

### 1. Configure secrets

Run the setup script and enter your Tesla app credentials when prompted:

```sh
./setup-secrets.sh
```

This creates the `secrets/` directory with your Client ID, Client Secret, and Redirect URI. These are mounted as Docker secrets and never baked into images.

### 2. DNS records

Create DNS records pointing to your Traefik host:

| Hostname | Purpose |
|----------|---------|
| `teslahitch.home.zenithnetwork.com` | Main app (Home Assistant access) |
| `teslaproxy.home.zenithnetwork.com` | Tesla HTTP proxy (vehicle commands from HA) |
| `tesla.zenithnetwork.com` | Tesla OAuth callback (TLS passthrough) |

### 3. Start the services

```sh
docker compose up -d
```

This starts two containers:

| Service | Internal Port | Traefik Route | Description |
|---------|---------------|---------------|-------------|
| `teslahitch` | 8080 (HTTPS), 8000 (HTTP) | `teslahitch.home.zenithnetwork.com` (inhttps), `tesla.zenithnetwork.com` (outhttps, TLS passthrough) | Main app — cert generation, OAuth, command routing, HA config |
| `tesla_http_proxy` | 4443 | `teslaproxy.home.zenithnetwork.com` (inhttps) | Tesla's vehicle-command proxy — sends encrypted commands to vehicles |

On first boot, `teslahitch` generates the fleet key pair and TLS certificate into a shared volume. The proxy container picks them up automatically.

### 4. Register your public key with Tesla

Once running, your public key is served at:

```
https://teslahitch.home.zenithnetwork.com/.well-known/appspecific/com.tesla.3p.public-key.pem
```

Follow Tesla's [documentation](https://developer.tesla.com/docs/fleet-api#register-a-public-key) to register this key with your application. Your domain must be publicly accessible for Tesla to fetch it.

### 5. Authenticate

Open in your browser:

```
http://localhost:8000/internal/auth
```

This redirects you to Tesla's login page. After you sign in and authorize, Tesla redirects back to your configured Redirect URI with an authorization code. The app exchanges it for access and refresh tokens automatically.

On startup, teslaHitch also registers your domain with Tesla's partner API. This is retried up to 5 times (with 15-second delays) to allow time for external routes to become available.

### 6. Set up Home Assistant

Install the [teslahacs](https://github.com/martinsaul/teslahacs) custom integration via HACS. During setup, enter:

- **teslaHitch URL**: `https://teslahitch.home.zenithnetwork.com`
- **Email**: your Tesla account email

The integration automatically fetches your access token, client ID, and proxy URL from teslaHitch. It re-fetches fresh tokens from teslaHitch before they expire — you should never need to re-authenticate unless the refresh token itself expires (90 days of inactivity).

## API Endpoints

### Public (port 8080 via Traefik)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Service status message |
| GET | `/health` | Health check (returns auth state and partner registration status) |
| GET | `/health/ready` | Readiness check (returns 503 if authenticated but partner not yet registered) |
| GET | `/.well-known/appspecific/com.tesla.3p.public-key.pem` | Fleet API public key |
| GET | `/api/ha/config` | Home Assistant config (access token, refresh token, expiration, client ID, proxy URL, domain) |

### Internal (port 8000)

These endpoints are only accessible on the trusted port. The `TrustedEndpointsFilter` blocks access to `/internal/` paths on the public port.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/internal/` | Status and auth state |
| GET | `/internal/auth` | Start OAuth flow (redirects to Tesla login) |
| GET | `/internal/callback?code=...&state=...` | OAuth callback (automatic — exchanges code for tokens) |
| POST | `/internal/vehicles/{vin}/command/{endpoint}` | Send a vehicle command |
| GET | `/internal/vehicles/{vin}/{endpoint}` | Read vehicle data |
| POST | `/internal/token/sync` | Sync tokens from external consumer (accepts `access_token`, `refresh_token`, `expires_in` or `expiration`) |

### `/api/ha/config` response

```json
{
  "access_token": "eyJhbGci...",
  "refresh_token": "NA_30fe...",
  "expiration": 1774085789381,
  "client_id": "36c9cd55-...",
  "proxy_url": "https://teslaproxy.home.zenithnetwork.com",
  "domain": "tesla.zenithnetwork.com"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `access_token` | string | A currently-valid Fleet API access token (auto-refreshed if within 5 min of expiry) |
| `refresh_token` | string | The current refresh token (for teslajsonpy initialization — do not use for independent refresh) |
| `expiration` | number | Access token expiration as Unix timestamp in milliseconds |
| `client_id` | string | Tesla app Client ID |
| `proxy_url` | string | External URL of the Tesla HTTP proxy for HA to send commands through |
| `domain` | string | Domain name in the TLS certificate SAN |

### Example: wake up a vehicle

```sh
curl -X POST http://localhost:8000/internal/vehicles/YOUR_VIN/command/wake_up
```

### Example: get vehicle data

```sh
curl http://localhost:8000/internal/vehicles/YOUR_VIN/vehicle_data
```

## Architecture

```
 Home Assistant (teslahacs)
        |
        | Polls /api/ha/config for fresh tokens (every 30 min before expiry)
        v
   teslahitch (Traefik: teslahitch.home.zenithnetwork.com)
        |                       |
        | Proactive refresh     | Vehicle commands via teslajsonpy
        | every 2h (scheduler)  |
        v                       v
   Tesla Auth Server    tesla_http_proxy (Traefik: teslaproxy.home.zenithnetwork.com)
                                |
                                | Encrypted vehicle commands
                                v
                          Tesla Cloud -> Vehicle

 Tesla OAuth Callback
        |
        | TLS passthrough (preserves self-signed cert)
        v
   teslahitch (Traefik: tesla.zenithnetwork.com, outhttps)
```

### Token flow

```
1. User authenticates via /internal/auth → Tesla OAuth → /internal/callback
2. teslaHitch stores access + refresh tokens in /config/auth.json
3. Scheduler force-refreshes every 2h → tokens always have ~8h of life
4. teslahacs polls /api/ha/config → gets fresh access token + correct expiration
5. teslahacs uses token for API calls through tesla_http_proxy
6. When token nears expiry, teslahacs re-fetches from /api/ha/config (not from Tesla)
```

### Port isolation

- **Port 8080** (HTTPS) is public-facing — serves the fleet public key, health check, and HA config endpoint
- **Port 8000** (HTTP) is the trusted internal port — exposes auth, vehicle commands, and token sync endpoints
- The `TrustedEndpointsFilter` enforces that `/internal/` paths are only reachable on the trusted port
- Tesla's OAuth callback uses TLS passthrough so Tesla sees the app's self-signed certificate directly

## Configuration

All configuration is in `application.properties` with environment variable / Docker secret overrides:

| Property | Default | Description |
|----------|---------|-------------|
| `tesla.oauth.clientId` | — | Tesla app Client ID |
| `tesla.oauth.clientSecret` | — | Tesla app Client Secret |
| `tesla.oauth.redirectUri` | — | OAuth redirect URI |
| `tesla.config.dir` | `/config` | Directory for certs and auth state (`auth.json`) |
| `tesla.proxy.url` | `https://localhost:4443` | Tesla HTTP proxy URL (internal) |
| `tesla.proxy.external.url` | `https://teslaproxy.home.zenithnetwork.com` | Tesla HTTP proxy URL (for HA) |
| `tesla.callback.hostname` | `tesla.zenithnetwork.com` | Hostname in TLS cert SAN for callback |
| `server.port` | `8080` | Public HTTPS port |
| `server.trustedPort` | `8000` | Internal/trusted HTTP port |

### Files in config directory

| File | Description |
|------|-------------|
| `auth.json` | Persisted OAuth state (access token, refresh token, expiration timestamps) |
| `fleet-key.pem` | EC private key (prime256v1) for Fleet API signing |
| `fleet-key-pub.pem` | EC public key served at `/.well-known/` |
| `tls-cert.pem` | Self-signed TLS certificate (secp521r1) |
| `tls-key.pem` | TLS private key |

## Development

Build and test locally:

```sh
./gradlew build
```

Run outside Docker (set env vars or create `secrets/` dir):

```sh
export TESLA_CLIENT_ID=your-id
export TESLA_CLIENT_SECRET=your-secret
export TESLA_REDIRECT_URI=https://your-domain.com/internal/callback
export TESLA_CONFIG_DIR=./config
./gradlew bootRun
```

## Troubleshooting

### Auth keeps getting lost

This was fixed in the token management overhaul. The root cause was:

1. The `/api/ha/config` endpoint returned a stale (already-expired) expiration timestamp
2. teslahacs thought the token was expired and refreshed directly with Tesla
3. Tesla's refresh token rotation invalidated teslaHitch's stored refresh token
4. teslaHitch's next refresh attempt failed — auth lost

If you still see auth issues, check the logs for:
- `Token refresh failed` — the refresh token may have been rotated by an external consumer. Re-authenticate via `/internal/auth`.
- `Force-refreshing access token (scheduled)...` / `Access token refreshed` — confirms the scheduler is working.
- `Scheduled token refresh failed` — check network connectivity to `auth.tesla.com`.

### Partner registration fails on startup

This is normal. The first 1-2 attempts usually fail because the Traefik route isn't ready yet. It retries up to 5 times with 15-second delays and typically succeeds by attempt 2-3.
