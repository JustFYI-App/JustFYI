#!/bin/bash
#
# Setup script for Just FYI git hooks
# This script installs the pre-push hook for the project.
#
# Usage:
#   ./scripts/setup-hooks.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
HOOKS_DIR="$PROJECT_ROOT/.git/hooks"

echo "Setting up git hooks for Just FYI..."

# Ensure .git/hooks directory exists
if [ ! -d "$HOOKS_DIR" ]; then
    echo "Error: .git/hooks directory not found. Is this a git repository?"
    exit 1
fi

# Install pre-push hook
if [ -f "$HOOKS_DIR/pre-push" ]; then
    echo "Backing up existing pre-push hook to pre-push.backup"
    mv "$HOOKS_DIR/pre-push" "$HOOKS_DIR/pre-push.backup"
fi

cp "$SCRIPT_DIR/pre-push" "$HOOKS_DIR/pre-push"
chmod +x "$HOOKS_DIR/pre-push"

echo "Pre-push hook installed successfully!"
echo ""
echo "The hook will run tests before each push."
echo "To skip the hook (use sparingly): git push --no-verify"
