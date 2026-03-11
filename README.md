# teslaHitch

A self-hosted service that generates the certificates needed to access Tesla's Fleet API and proxies vehicle commands. Designed to run alongside [tesla/vehicle-command](https://github.com/teslamotors/vehicle-command) and integrate with Home Assistant via [teslahacs](https://github.com/martinsaul/teslahacs).

## What it does

1. **Generates an EC key pair** (prime256v1) on first startup — this is the "fleet key" Tesla requires for vehicle commands
2. **Generates a self-signed TLS certificate** for the Tesla HTTP proxy and Spring Boot HTTPS
3. **Serves your public key** at `/.well-known/appspecific/com.tesla.3p.public-key.pem` so you can register it with Tesla
4. **Handles the full OAuth 2.0 flow** with Tesla's auth servers (token exchange + refresh)
5. **Proxies vehicle commands** through the Tesla HTTP proxy to your car
6. **Provides a config endpoint** (`/api/ha/config`) for automatic Home Assistant integration setup

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

### 6. Set up Home Assistant

Install the [teslahacs](https://github.com/martinsaul/teslahacs) custom integration via HACS. During setup, enter:

- **teslaHitch URL**: `https://teslahitch.home.zenithnetwork.com`
- **Email**: your Tesla account email

The integration automatically fetches your refresh token, client ID, and proxy URL from teslahitch.

## API Endpoints

### Public (port 8080 via Traefik)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Health check |
| GET | `/.well-known/appspecific/com.tesla.3p.public-key.pem` | Fleet API public key |
| GET | `/api/ha/config` | Home Assistant config (refresh token, client ID, proxy URL) |

### Internal (port 8000)

These endpoints are only accessible on the trusted port.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/internal/` | Status and auth state |
| GET | `/internal/auth` | Start OAuth flow |
| GET | `/internal/callback?code=...&state=...` | OAuth callback (automatic) |
| POST | `/internal/vehicles/{vin}/command/{endpoint}` | Send a vehicle command |
| GET | `/internal/vehicles/{vin}/{endpoint}` | Read vehicle data |

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
        | Fetches config from /api/ha/config
        v
   teslahitch (Traefik: teslahitch.home.zenithnetwork.com)
        |
        | Vehicle commands via teslajsonpy
        v
   tesla_http_proxy (Traefik: teslaproxy.home.zenithnetwork.com)
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

- **Port 8080** (HTTPS) is public-facing — serves the fleet public key, health check, and HA config endpoint
- **Port 8000** (HTTP) is the trusted internal port — exposes auth and vehicle command endpoints
- The `TrustedEndpointsFilter` enforces that `/internal/` paths are only reachable on the trusted port
- Tesla's OAuth callback uses TLS passthrough so Tesla sees the app's self-signed certificate directly

## Configuration

All configuration is in `application.properties` with environment variable / Docker secret overrides:

| Property | Default | Description |
|----------|---------|-------------|
| `tesla.oauth.clientId` | — | Tesla app Client ID |
| `tesla.oauth.clientSecret` | — | Tesla app Client Secret |
| `tesla.oauth.redirectUri` | — | OAuth redirect URI |
| `tesla.config.dir` | `/config` | Directory for certs and auth state |
| `tesla.proxy.url` | `https://localhost:4443` | Tesla HTTP proxy URL (internal) |
| `tesla.proxy.external.url` | `https://teslaproxy.home.zenithnetwork.com` | Tesla HTTP proxy URL (for HA) |
| `tesla.callback.hostname` | `tesla.zenithnetwork.com` | Hostname in TLS cert SAN for callback |
| `server.port` | `8080` | Public HTTPS port |
| `server.trustedPort` | `8000` | Internal/trusted HTTP port |

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
