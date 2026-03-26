#!/bin/bash
#
# GJP API Admin - Run Script
# ===========================
# Starts the gjp-api-admin-springboot application.
#
# Configuration is managed via Spring profile YAML files:
#   application-dev.yml  - Local development (requires env vars: MYSQL_USERNAME, MYSQL_PASSWORD, JWT_SECRET_KEY)
#   application-prod.yml - Production (requires env vars: DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET_KEY)
#
# Usage:
#   ./run.sh          # run with dev profile (default)
#   ./run.sh --prod   # run with prod profile (requires env vars)
#   ./run.sh --stop   # stop any running instance on port 8082

set -euo pipefail

# ── Load MYSQL env vars from zsh profile if not already set ──────────────────
if [[ -z "${MYSQL_USERNAME:-}" ]]; then
    MYSQL_USERNAME=$(zsh -lc 'echo $MYSQL_USERNAME' 2>/dev/null || true)
    export MYSQL_USERNAME
fi
if [[ -z "${MYSQL_PASSWORD:-}" ]]; then
    MYSQL_PASSWORD=$(zsh -lc 'echo $MYSQL_PASSWORD' 2>/dev/null || true)
    export MYSQL_PASSWORD
fi
if [[ -z "${JWT_SECRET_KEY:-}" ]]; then
    JWT_SECRET_KEY=$(zsh -lc 'echo $JWT_SECRET_KEY' 2>/dev/null || true)
    export JWT_SECRET_KEY
fi

# ── Resolve project directory (parent of scripts/) ─────────────────────────
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${PROJECT_DIR}"

# ── Parse arguments ──────────────────────────────────────────────────────────
SPRING_PROFILE="dev"
STOP_ONLY=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --prod)  SPRING_PROFILE="prod"; shift ;;
        --dev)   SPRING_PROFILE="dev";  shift ;;
        --stop)  STOP_ONLY=true;        shift ;;
        --help)
            echo "Usage: $0 [--dev|--prod] [--stop]"
            echo ""
            echo "Options:"
            echo "  --dev     Run with 'dev' profile (default)"
            echo "  --prod    Run with 'prod' profile"
            echo "  --stop    Stop any running instance on port 8082 and exit"
            echo "  --help    Show this help message"
            echo ""
            echo "Dev profile  : requires environment variables:"
            echo "  MYSQL_USERNAME  MySQL username"
            echo "  MYSQL_PASSWORD  MySQL password"
            echo "  JWT_SECRET_KEY  JWT signing key (generate with ./scripts/generate-jwt-key.sh)"
            echo ""
            echo "Prod profile : requires environment variables:"
            echo "  DB_URL          MySQL JDBC URL"
            echo "  DB_USERNAME     MySQL username"
            echo "  DB_PASSWORD     MySQL password"
            echo "  JWT_SECRET_KEY  JWT signing key (min 32 chars)"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# ── Stop any running instance on port 8082 ───────────────────────────────────
PIDS=$(lsof -ti:8082 2>/dev/null || true)
if [[ -n "${PIDS}" ]]; then
    echo "Stopping existing instance on port 8082 (PID: ${PIDS})..."
    echo "${PIDS}" | xargs kill -9 2>/dev/null || true
    sleep 1
    echo "Stopped."
fi

if [[ "${STOP_ONLY}" == true ]]; then
    [[ -z "${PIDS}" ]] && echo "No running instance found on port 8082."
    exit 0
fi

# ── Validate environment variables ────────────────────────────────────────────
MISSING=()

if [[ "${SPRING_PROFILE}" == "dev" ]]; then
    [[ -z "${MYSQL_USERNAME:-}" ]] && MISSING+=("MYSQL_USERNAME")
    [[ -z "${MYSQL_PASSWORD:-}" ]] && MISSING+=("MYSQL_PASSWORD")
    [[ -z "${JWT_SECRET_KEY:-}" ]] && MISSING+=("JWT_SECRET_KEY")

    if [[ ${#MISSING[@]} -gt 0 ]]; then
        echo "ERROR: Dev profile requires the following environment variables:"
        for var in "${MISSING[@]}"; do
            echo "  - ${var}"
        done
        echo ""
        echo "Export them before running:"
        echo "  export MYSQL_USERNAME=root"
        echo "  export MYSQL_PASSWORD=your_password"
        echo "  export JWT_SECRET_KEY=\$(openssl rand -base64 48)"
        echo ""
        echo "Or generate a JWT secret key with:"
        echo "  ./scripts/generate-jwt-key.sh"
        exit 1
    fi
elif [[ "${SPRING_PROFILE}" == "prod" ]]; then
    [[ -z "${DB_URL:-}" ]]         && MISSING+=("DB_URL")
    [[ -z "${DB_USERNAME:-}" ]]    && MISSING+=("DB_USERNAME")
    [[ -z "${DB_PASSWORD:-}" ]]    && MISSING+=("DB_PASSWORD")
    [[ -z "${JWT_SECRET_KEY:-}" ]] && MISSING+=("JWT_SECRET_KEY")

    if [[ ${#MISSING[@]} -gt 0 ]]; then
        echo "ERROR: Production profile requires the following environment variables:"
        for var in "${MISSING[@]}"; do
            echo "  - ${var}"
        done
        echo ""
        echo "Export them before running:"
        echo "  export DB_URL=jdbc:mysql://host:3306/gjp_db?useSSL=false&serverTimezone=UTC"
        echo "  export DB_USERNAME=your_username"
        echo "  export DB_PASSWORD=your_password"
        echo "  export JWT_SECRET_KEY=your_jwt_secret_key_at_least_32_chars"
        exit 1
    fi
fi

# ── Display startup info ────────────────────────────────────────────────────
echo ""
echo "============================================"
echo "  GJP API Admin - Starting"
echo "============================================"
echo ""
echo "Profile  : ${SPRING_PROFILE}"
echo "Port     : 8082"
echo "Context  : /api/"
echo ""

# ── Run the application ─────────────────────────────────────────────────────
echo "Starting application..."
echo ""

./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=${SPRING_PROFILE}"
