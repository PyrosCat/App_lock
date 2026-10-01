#!/usr/bin/env bash
# Restores what an unfinished R-007 device run (p1_validate.sh or p2_device.sh) left changed: the device settings, and
# the app data that the markers in files/r007/pending/ name.
#
# Before it changes the stay-awake and rotation settings, a run records their original values, and those of the two
# accessibility settings, on the device in /data/local/tmp/r007_settings.pending. Its exit handler restores them and
# removes the record, also after an early stop or an INT, TERM, or HUP signal. The record stays after a crash or a
# SIGKILL, and when the handler cannot restore the settings or save the comparison. The next run then stops before it
# changes anything. In the same way, a run writes a marker before it changes app data that it must restore (a saved
# store copy, the saved app settings file, a read-only preferences directory; see lib_r007.sh), and the next run does
# not start while a marker exists.
#
# This script first undoes the app-data changes of the markers, each with its check, and removes each marker after its
# check. Then it writes the recorded values back, reads them again, and removes the record only when every value
# matches and the comparison is saved in build/r007-evidence/restore_settings-<serial>-<UTC time>.log. A match shows
# that the changes were undone later. It does not remove the failure of the earlier run or make that run valid.
#
# Usage: scripts/r007/restore_settings.sh [-s SERIAL]
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; source "$HERE/lib_p2.sh"
while getopts "s:" opt; do case "$opt" in s) SERIAL="$OPTARG";; *) exit 2;; esac; done

require_device || exit 2
r007_evidence_init "restore_settings" || { summary "R-007 settings restore"; exit 1; }
# App data first, as in the exit handler of a run: its restores stop the app, and a force-stop drops a detector that
# the restored accessibility settings enable.
r007_recover_pending
if ! state="$(r007_settings_record_state)"; then
  fail "the settings record could not be queried"
elif [ "$state" = absent ]; then
  info "no settings record: no earlier run left the device settings changed"
else
  info "record: $(sh_ cat "$R007_SETTINGS_RECORD" | tr '\n' ' ')"
  r007_settings_restore
fi
summary "R-007 settings restore"
