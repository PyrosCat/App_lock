#!/usr/bin/env bash
# Restores the device settings that an unfinished p1_validate.sh run left changed (R-007 test plan phase P1).
#
# Before it changes the stay-awake and rotation settings, p1_validate.sh records their original values on the device
# in /data/local/tmp/r007_settings.pending. Its exit handler restores them and removes the record, also after an early
# stop or an INT, TERM, or HUP signal. The record stays after a crash or a SIGKILL, and when the handler cannot restore
# the settings or save the comparison. The next p1_validate.sh run then stops before it changes anything.
#
# This script writes the recorded values back, reads them again, and removes the record only when every value
# matches and the comparison is saved in build/r007-evidence/restore_settings-<serial>-<UTC time>.log. A match shows
# that the settings were restored later. It does not remove the failure of the earlier run or make that run valid.
#
# Usage: scripts/r007/restore_settings.sh [-s SERIAL]
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib_r007.sh"
while getopts "s:" opt; do case "$opt" in s) SERIAL="$OPTARG";; *) exit 2;; esac; done

require_device || exit 2
r007_evidence_init "restore_settings" || { summary "R-007 settings restore"; exit 1; }
if ! state="$(r007_settings_record_state)"; then
  fail "the settings record could not be queried"
elif [ "$state" = absent ]; then
  info "no settings record: no earlier run left the device settings changed"
else
  info "record: $(sh_ cat "$R007_SETTINGS_RECORD" | tr '\n' ' ')"
  r007_settings_restore
fi
summary "R-007 settings restore"
