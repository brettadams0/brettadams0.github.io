#!/usr/bin/env bash
# border-waits/ must be exactly border-waits-pipeline's web/, minus its development files:
#   .github/scripts/check-border-waits-sync.sh ../border-waits-pipeline
# The exclusions are the ones in the README's rsync line. Prints what differs and exits 1, or exits 0.
set -euo pipefail
upstream=${1:?usage: check-border-waits-sync.sh <border-waits-pipeline checkout>}
diff=$(rsync -rcn --delete --itemize-changes \
  --exclude node_modules --exclude test --exclude package.json --exclude package-lock.json --exclude tsconfig.json --exclude Makefile \
  "$upstream/web/" border-waits/)
if [ -n "$diff" ]; then
  echo "border-waits/ differs from $upstream/web/ (>f: upstream is newer or different, *deleting: only here):"
  echo "$diff"
  exit 1
fi
echo "border-waits/ matches $upstream/web/ at $(git -C "$upstream" rev-parse --short HEAD 2>/dev/null || echo '?')"
