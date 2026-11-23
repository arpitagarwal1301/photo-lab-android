# Spike D — Paparazzi as the offline "screen factory"

**Question:** can we render our own screens on the JVM (no device) and get, for each one:

1. a PNG snapshot,
2. animated frames with known timing (`paparazzi.gif`, saved as animated PNG),
3. the TalkBack-order accessibility legend, and
4. **the semantics tree as JSON**, plus the compact text an agent would send?

If yes, Paparazzi becomes the QA-fixture factory, the offline test bed for the pruner and stability detector, and (Phase 7) a training-data generator.

## Run

```bash
./gradlew :spikes:d-paparazzi-semantics:recordPaparazziDebug
```

Outputs:

- Snapshots: `spikes/d-paparazzi-semantics/src/test/snapshots/images/`
- Animated frames (APNG): `spikes/d-paparazzi-semantics/src/test/snapshots/videos/`
- Semantics: `spikes/d-paparazzi-semantics/build/spike-d/checkout.json` and `.txt`
- The test log prints `nodes=… compact_chars=… approx_tokens=… fingerprint=…`

## The fixture

`CheckoutScreen` has two seeded bugs: an unlabelled tappable control, and a pay button whose total (₹1,499) doesn't match the order total (₹1,498). The compact text should show `(no label!)` for the first. The second is the kind of bug the QA agent must reason about.

## Limits to remember

- Paparazzi renders **our own** composables/views only. It can't see other apps or a live device.
- layoutlib rendering is close to, but not the same as, a real device.
