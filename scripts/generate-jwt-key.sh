#!/bin/bash
#
# Generate JWT Secret Key
# =======================
# Generates a cryptographically secure Base64-encoded key for JWT signing (HS256).
# The key is 384 bits (48 bytes), exceeding the 256-bit minimum for HMAC-SHA256.
#
# Usage:
#   ./generate-jwt-key.sh            # print the key
#   ./generate-jwt-key.sh --export   # print export command for shell profile

set -euo pipefail

KEY=$(openssl rand -base64 48)

if [[ "${1:-}" == "--export" ]]; then
    echo "Add this line to your shell profile (~/.zshrc or ~/.bashrc):"
    echo ""
    echo "  export JWT_SECRET_KEY=\"${KEY}\""
    echo ""
    echo "Then reload your shell:"
    echo "  source ~/.zshrc"
else
    echo ""
    echo "JWT Secret Key (Base64, 384-bit):"
    echo ""
    echo "  ${KEY}"
    echo ""
    echo "To export as environment variable:"
    echo "  export JWT_SECRET_KEY=\"${KEY}\""
    echo ""
    echo "Or run with --export flag to get the full export command."
fi
