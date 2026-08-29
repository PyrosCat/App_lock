#!/usr/bin/env bash
# Put the Google Cloud SDK's bin on PATH for the CURRENT shell, so `gcloud` resolves without
# opening a fresh terminal (a fresh gcloud install updates the Windows PATH, but shells already
# open — including Git Bash — don't see it until restarted).
#
# SOURCE it (don't execute) so the PATH change sticks in your shell:
#   . scripts/ftl/gcloud-env.sh        # or:  source scripts/ftl/gcloud-env.sh
#
# run_ov4_ftl.sh sources this automatically when gcloud isn't already on PATH, so usually you
# don't need to run it by hand.

# Already resolvable? Nothing to do.
if command -v gcloud >/dev/null 2>&1; then
  return 0 2>/dev/null || exit 0
fi

# Normalize a Windows-style dir to a Git Bash path when cygpath is available.
_gce_u() { command -v cygpath >/dev/null 2>&1 && cygpath -u "$1" || echo "$1"; }

# Candidate SDK bin dirs, most-specific first. $LOCALAPPDATA is the default per-user install.
_gce_candidates=()
[ -n "${LOCALAPPDATA:-}" ] && _gce_candidates+=("$(_gce_u "$LOCALAPPDATA")/Google/Cloud SDK/google-cloud-sdk/bin")
_gce_candidates+=(
  "$HOME/AppData/Local/Google/Cloud SDK/google-cloud-sdk/bin"
  "/c/Program Files (x86)/Google/Cloud SDK/google-cloud-sdk/bin"
  "/c/Program Files/Google/Cloud SDK/google-cloud-sdk/bin"
  "$HOME/google-cloud-sdk/bin"
)

for _gce_bin in "${_gce_candidates[@]}"; do
  if [ -x "$_gce_bin/gcloud" ] || [ -f "$_gce_bin/gcloud.cmd" ]; then
    export PATH="$_gce_bin:$PATH"
    echo "gcloud-env: added to PATH -> $_gce_bin"
    unset _gce_candidates _gce_bin
    unset -f _gce_u 2>/dev/null
    return 0 2>/dev/null || exit 0
  fi
done

echo "gcloud-env: could not find the Cloud SDK bin dir. Install it (https://cloud.google.com/sdk/docs/install)" >&2
echo "gcloud-env: or edit the candidate list in scripts/ftl/gcloud-env.sh." >&2
unset _gce_candidates _gce_bin
unset -f _gce_u 2>/dev/null
return 1 2>/dev/null || exit 1
