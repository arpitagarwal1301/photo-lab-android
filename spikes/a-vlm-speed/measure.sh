#!/system/bin/sh
# Runs ON THE PHONE. Usage: measure.sh <out.log> <command...>
# Runs the command, polls its peak resident memory (VmHWM) once a second,
# and appends PEAK_RSS_KB and EXIT_CODE to the log.
OUT="$1"
shift
"$@" > "$OUT" 2>&1 &
PID=$!
PEAK=0
while kill -0 "$PID" 2>/dev/null; do
  H=$(grep VmHWM "/proc/$PID/status" 2>/dev/null | sed 's/[^0-9]//g')
  [ -n "$H" ] && PEAK="$H"
  sleep 1
done
wait "$PID"
RC=$?
echo "PEAK_RSS_KB=$PEAK" >> "$OUT"
echo "EXIT_CODE=$RC" >> "$OUT"
