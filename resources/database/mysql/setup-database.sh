#!/bin/bash
#
# GJP Database Setup Script
# =========================
# Creates the gjp_db database and all tables with seed data.
#
# Environment variables:
#   MYSQL_USERNAME  - MySQL username (default: root)
#   MYSQL_PASSWORD  - MySQL password (omit to be prompted)
#
# Usage:
#   ./setup-database.sh                          # uses env vars, prompts for password if not set
#   ./setup-database.sh -h 192.168.1.5           # custom host
#   export MYSQL_USERNAME=myuser MYSQL_PASSWORD=secret && ./setup-database.sh
#
# SQL execution order:
#   1. 00-gjp-db.sql   - Create database
#   2. 01-gjp-auth.sql - Auth tables (users, roles, user_roles, audit_logs, refresh_tokens) + seed data
#   3. 01-gjp-master.sql - Master data tables (app_settings) + seed data
#   4. 01-gjp-cms.sql   - CMS tables (website, logo, image, video, audio, article, file, article_image, question)

set -euo pipefail

# ── Defaults (from environment variables) ─────────────────────────────────────
MYSQL_USER="${MYSQL_USERNAME:-root}"
MYSQL_HOST="localhost"
MYSQL_PORT="3306"
MYSQL_PASS="${MYSQL_PASSWORD:-}"

# ── Parse arguments ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--host)     MYSQL_HOST="$2";  shift 2 ;;
        -P|--port)     MYSQL_PORT="$2";  shift 2 ;;
        --help)
            echo "Usage: $0 [-h host] [-P port]"
            echo ""
            echo "Options:"
            echo "  -h, --host      MySQL host (default: localhost)"
            echo "  -P, --port      MySQL port (default: 3306)"
            echo "  --help          Show this help message"
            echo ""
            echo "Environment variables:"
            echo "  MYSQL_USERNAME  MySQL username (default: root)"
            echo "  MYSQL_PASSWORD  MySQL password (omit to be prompted)"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# ── Resolve script directory ─────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── SQL files in execution order ─────────────────────────────────────────────
SQL_FILES=(
    "00-gjp-db.sql"
    "01-gjp-auth.sql"
    "01-gjp-master.sql"
    "01-gjp-cms.sql"
)

# ── Verify all SQL files exist before starting ───────────────────────────────
echo "============================================"
echo "  GJP Database Setup"
echo "============================================"
echo ""
echo "Host : ${MYSQL_HOST}:${MYSQL_PORT}"
echo "User : ${MYSQL_USER}"
echo ""

for sql_file in "${SQL_FILES[@]}"; do
    if [[ ! -f "${SCRIPT_DIR}/${sql_file}" ]]; then
        echo "ERROR: SQL file not found: ${SCRIPT_DIR}/${sql_file}"
        exit 1
    fi
done
echo "All SQL files verified."
echo ""

# ── Build mysql command ──────────────────────────────────────────────────────
MYSQL_CMD="mysql --host=${MYSQL_HOST} --port=${MYSQL_PORT} --user=${MYSQL_USER}"

if [[ -n "${MYSQL_PASS}" ]]; then
    MYSQL_CMD="${MYSQL_CMD} --password=${MYSQL_PASS}"
else
    # Prompt for password if MYSQL_PASSWORD env var is not set
    echo -n "Enter MySQL password for '${MYSQL_USER}': "
    read -rs MYSQL_PASS
    echo ""
    MYSQL_CMD="${MYSQL_CMD} --password=${MYSQL_PASS}"
fi

# ── Confirm before proceeding (this will DROP the database) ──────────────────
echo ""
echo "WARNING: This will DROP and recreate the 'gjp_db' database."
echo "         All existing data in 'gjp_db' will be lost!"
echo ""
echo -n "Continue? (y/N): "
read -r CONFIRM
if [[ "${CONFIRM}" != "y" && "${CONFIRM}" != "Y" ]]; then
    echo "Aborted."
    exit 0
fi
echo ""

# ── Execute SQL files ────────────────────────────────────────────────────────
for sql_file in "${SQL_FILES[@]}"; do
    echo -n "Running ${sql_file} ... "
    if ${MYSQL_CMD} < "${SCRIPT_DIR}/${sql_file}" 2>&1; then
        echo "OK"
    else
        echo "FAILED"
        echo "ERROR: Failed to execute ${sql_file}. Aborting."
        exit 1
    fi
done

echo ""
echo "============================================"
echo "  Database setup completed successfully!"
echo "============================================"
echo ""
echo "Database : gjp_db"
echo "Tables created:"
echo "  - auth_users"
echo "  - auth_roles"
echo "  - auth_user_roles"
echo "  - audit_logs"
echo "  - auth_refresh_tokens"
echo "  - master_app_settings"
echo "  - cms_website"
echo "  - cms_logo"
echo "  - cms_image"
echo "  - cms_video"
echo "  - cms_audio"
echo "  - cms_article"
echo "  - cms_file"
echo "  - cms_article_image"
echo "  - cms_question"
echo ""
echo "Seed data inserted:"
echo "  - Super admin user (username: superadmin, password: 123456)"
echo "  - 10 predefined roles (SUPER_ADMIN, ADMIN, etc.)"
echo "  - App settings (EN + ZH)"
