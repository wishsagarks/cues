# Sprint 5 device runbook

The Sprint 7 rows (package discovery, NPU, camera, Office Kit, Origin Island,
accessibility, Jovi/AppFunctions, pending actions, QR) and the `./dev probe`
evidence protocol are in [DEVICE_MATRIX.md](DEVICE_MATRIX.md). The live demo
and its cuts are in [DEMO.md](DEMO.md).

This is a protocol, not a measurement record. Do not create or populate
`MEASUREMENTS.md` until the loaner device produces the observations below.

1. Run the parser and side-loaded-model bake-offs against `paraphrases.txt`
   and independently authored `unseen.txt`. Record device, OS build, model
   file, runtime/backend, airplane/network state, pass counts, wrong-meaning
   accepts, p50 and p95. A model that fails the bar stays inert.
2. For the hero cue, repeat five matching starts, three non-matches and two
   deadline exits. Time each from the triggering platform event in logcat to
   the visible receipt/action result using a stopwatch. Record the method and
   caveats with every value.
3. Arm an any-Wi-Fi cue and an at-time cue; test foreground and screen-off
   delivery. Confirm the monitoring card transitions, receipts, unknown Wi-Fi
   behavior and the time adapter's next-occurrence rescheduling.
4. Run the dry-run before and after checking platform state. It must create no
   zen rule, alarm, service or notification side effect.
5. Rehearse the demo twice. If footage is used as backup, put “Recorded” on
   screen; never present it as live delivery.

Measurement record schema once real observations exist: date, device, OS
build, app commit, method, value, units, source/log reference and caveat.
