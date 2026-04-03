# teslaHitch

A self-hosted Tesla Fleet API proxy that manages OAuth authentication, generates certificates, and proxies vehicle commands. Runs alongside [tesla/vehicle-command](https://github.com/teslamotors/vehicle-command) and integrates with Home Assistant via [teslahacs](https://github.com/martinsaul/teslahacs).

## What it does

1. **Generates an EC key pair** (prime256v1) on first startup -- the "fleet key" Tesla requires for vehicle commands
2. **Generates a self-signed TLS certificate** for the Tesla HTTP proxy and Spring Boot HTTPS
3. **Serves your public key** at `/.well-known/appspecific/com.tesla.3p.public-key.pem`
4. **Handles the full OAuth 2.0 flow** with Tesla's auth servers
5. **Manages token lifecycle** -- proactively refreshes every 2 hours so tokens are always valid
6. **Lists vehicles and energy sites** via the Fleet API
7. **Proxies vehicle commands** through the Tesla HTTP proxy
8. **Provides a config endpoint** (`/internal/ha/config`) for automatic Home Assistant setup

## Token Management

teslaHitch is the **sole token authority**. It owns the OAuth session with Tesla and is the only service that refreshes tokens. This is critical because Tesla uses **refresh token rotation** -- every refresh invalidates the previous token.

- A **scheduled job** force-refreshes every 2 hours. Since access tokens last ~8h, tokens always have plenty of remaining life.
- All refresh operations are **thread-safe** (guarded by `ReentrantLock`).
- The `/internal/ha/config` endpoint returns a **currently-valid access token** with correct expiration.
- teslahacs re-fetches tokens from this endpoint -- it never refreshes directly with Tesla.

## Prerequisites

- Docker and Docker Compose
- A Tesla developer account at [developer.tesla.com](https://developer.tesla.com)
- A registered application with Client ID, Client Secret, and Redirect URI
- A reverse proxy (Traefik) with an external `traefik` Docker network

## Setup

### 1. Configure secrets

```sh
./setup-secrets.sh
```

Creates the `secrets/` directory with your Tesla app credentials, mounted as Docker secrets.

### 2. DNS records

| Hostname | Purpose |
|----------|---------|
| `teslahitch.home.zenithnetwork.com` | Public endpoints |
| `teslaproxy.home.zenithnetwork.com` | Tesla HTTP proxy (vehicle commands) |
| `tesla.zenithnetwork.com` | OAuth callback (TLS passthrough) |

### 3. Start the services

```sh
docker compose up -d
```

| Service | Ports | Description |
|---------|-------|-------------|
| `teslahitch` | 8080 (HTTPS), 8000 (HTTP) | OAuth, cert generation, command routing, HA config |
| `tesla_http_proxy` | 4443 | Tesla's vehicle-command proxy |

### 4. Register your public key

Your key is served at `https://teslahitch.home.zenithnetwork.com/.well-known/appspecific/com.tesla.3p.public-key.pem`. Register it with Tesla per their [documentation](https://developer.tesla.com/docs/fleet-api#register-a-public-key).

### 5. Authenticate

Open `http://localhost:8000/internal/auth` in your browser. Sign in with Tesla and authorize. Tokens are stored automatically.

### 6. Set up Home Assistant

Install [teslahacs](https://github.com/martinsaul/teslahacs) via HACS. During setup, enter:

- **teslaHitch URL**: `http://<host>:8000` (must be the trusted port)
- **Email**: your Tesla account email

## API Endpoints

### Public (port 8080)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Status message |
| GET | `/health` | Auth state + partner registration status |
| GET | `/health/ready` | Readiness probe (200 or 503) |
| GET | `/.well-known/appspecific/com.tesla.3p.public-key.pem` | Fleet API public key |

### Trusted (port 8000)

All `/internal/*` paths are blocked on port 8080 by `TrustedEndpointsFilter`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/internal/` | Status + auth state |
| GET | `/internal/auth` | Start OAuth flow |
| GET | `/internal/callback` | OAuth callback (automatic) |
| GET | `/internal/ha/config` | HA config (tokens, client ID, proxy URL) |
| GET | `/internal/products` | List all vehicles and energy sites |
| POST | `/internal/vehicles/{vin}/command/{endpoint}` | Send vehicle command |
| GET | `/internal/vehicles/{vin}/{endpoint}` | Read vehicle data |
| POST | `/internal/token/sync` | Sync tokens from external consumer |

**Input validation:** VINs validated as `[A-Za-z0-9]{5,17}`, endpoints as `[a-zA-Z0-9_/]+`.

### `/internal/ha/config` response

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

### Examples

```sh
# Wake up a vehicle
curl -X POST http://localhost:8000/internal/vehicles/YOUR_VIN/command/wake_up

# Get vehicle data
curl http://localhost:8000/internal/vehicles/YOUR_VIN/vehicle_data

# List all products
curl http://localhost:8000/internal/products
```

## Architecture

```
teslahacs (Home Assistant)
    |
    | All calls go to port 8000
    |
    |-- GET /internal/ha/config          -> fresh tokens
    |-- GET /internal/products           -> list vehicles
    |-- GET /internal/vehicles/{vin}/... -> vehicle data
    |-- POST /internal/vehicles/{vin}/command/... -> commands
    |
    v
teslaHitch (port 8000, trusted)
    |                       |
    | Token refresh         | Vehicle commands
    | (scheduler, 2h)       |
    v                       v
Tesla Auth Server      tesla_http_proxy (port 4443)
                            |
                            | Encrypted commands
                            v
                      Tesla Cloud -> Vehicle
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `tesla.oauth.clientId` | (secret) | Tesla app Client ID |
| `tesla.oauth.clientSecret` | (secret) | Tesla app Client Secret |
| `tesla.oauth.redirectUri` | (secret) | OAuth redirect URI |
| `tesla.config.dir` | `/config` | Cert and auth storage directory |
| `tesla.proxy.url` | `https://localhost:4443` | Internal proxy URL |
| `tesla.proxy.external.url` | — | External proxy URL for HA |
| `tesla.callback.hostname` | `tesla.zenithnetwork.com` | TLS certificate SAN |
| `tesla.fleet.locale` | `NAAP` | Fleet API region (NAAP/EMEA/CHINA) |
| `server.port` | `8080` | Public HTTPS port |
| `server.trustedPort` | `8000` | Trusted HTTP port |

### Config directory files

| File | Description |
|------|-------------|
| `auth.json` | OAuth state (tokens, expiration) |
| `fleet-key.pem` / `fleet-key-pub.pem` | Fleet API EC key pair |
| `tls-cert.pem` / `tls-key.pem` | Self-signed TLS certificate |
| `partner-registered.json` | Partner registration state |
