#!/bin/bash
#
# Generate BCrypt Password Hash
# ==============================
# Generates a BCrypt hash for a given plain-text password.
# The hash can be used directly in the auth_users.password_hash column.
#
# Requires: Python 3 with bcrypt module, or htpasswd, or Spring Boot CLI
#
# Usage:
#   ./generate-password.sh                # prompt for password
#   ./generate-password.sh "MyP@ss123"    # hash the provided password

set -euo pipefail

# ── Get password ─────────────────────────────────────────────────────────────
if [[ $# -gt 0 ]]; then
    PASSWORD="$1"
else
    echo -n "Enter password to hash: "
    read -rs PASSWORD
    echo ""
    if [[ -z "${PASSWORD}" ]]; then
        echo "ERROR: Password cannot be empty."
        exit 1
    fi
fi

# ── Validate password complexity ─────────────────────────────────────────────
if [[ ${#PASSWORD} -lt 6 ]]; then
    echo "ERROR: Password must be at least 6 characters."
    exit 1
fi

HAS_LOWER=$(echo "${PASSWORD}" | grep -q '[a-z]' && echo 1 || echo 0)
HAS_UPPER=$(echo "${PASSWORD}" | grep -q '[A-Z]' && echo 1 || echo 0)
HAS_DIGIT=$(echo "${PASSWORD}" | grep -q '[0-9]' && echo 1 || echo 0)
HAS_SPECIAL=$(echo "${PASSWORD}" | grep -q '[@$!%*?&]' && echo 1 || echo 0)
if [[ "${HAS_LOWER}" -eq 0 || "${HAS_UPPER}" -eq 0 || "${HAS_DIGIT}" -eq 0 || "${HAS_SPECIAL}" -eq 0 ]]; then
    echo "WARNING: Password should contain at least one uppercase, one lowercase, one digit, and one special character (@\$!%*?&)."
fi

# ── Generate BCrypt hash ────────────────────────────────────────────────────
# Try Python 3 with bcrypt first
if command -v python3 &>/dev/null; then
    # Check if bcrypt module is available
    if python3 -c "import bcrypt" 2>/dev/null; then
        HASH=$(echo -n "${PASSWORD}" | python3 -c "
import sys, bcrypt
password = sys.stdin.buffer.read()
salt = bcrypt.gensalt(rounds=10)
hashed = bcrypt.hashpw(password, salt)
print(hashed.decode('utf-8'))
")
        echo ""
        echo "BCrypt Hash (cost factor 10):"
        echo ""
        echo "  ${HASH}"
        echo ""
        echo "SQL insert example:"
        echo "  UPDATE auth_users SET password_hash = '${HASH}' WHERE username = 'your_user';"
        echo ""
        exit 0
    fi

    # Fallback: try passlib
    if python3 -c "from passlib.hash import bcrypt" 2>/dev/null; then
        HASH=$(echo -n "${PASSWORD}" | python3 -c "
import sys
from passlib.hash import bcrypt
password = sys.stdin.read()
print(bcrypt.using(rounds=10).hash(password))
")
        echo ""
        echo "BCrypt Hash (cost factor 10):"
        echo ""
        echo "  ${HASH}"
        echo ""
        echo "SQL insert example:"
        echo "  UPDATE auth_users SET password_hash = '${HASH}' WHERE username = 'your_user';"
        echo ""
        exit 0
    fi
fi

# Try htpasswd (Apache utilities)
if command -v htpasswd &>/dev/null; then
    HASH=$(htpasswd -nbBC 10 "" "${PASSWORD}" | tr -d ':\n' | sed 's/$2y/$2a/')
    echo ""
    echo "BCrypt Hash (cost factor 10):"
    echo ""
    echo "  ${HASH}"
    echo ""
    echo "SQL insert example:"
    echo "  UPDATE auth_users SET password_hash = '${HASH}' WHERE username = 'your_user';"
    echo ""
    exit 0
fi

# No tool found
echo "ERROR: No BCrypt tool found. Install one of the following:"
echo ""
echo "  Option 1 (recommended): pip3 install bcrypt"
echo "  Option 2: pip3 install passlib"
echo "  Option 3: brew install httpd   (provides htpasswd)"
echo ""
exit 1
