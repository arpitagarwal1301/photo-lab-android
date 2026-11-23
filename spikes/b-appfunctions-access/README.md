# Spike B — Can a normal app call other apps' AppFunctions?

**Question:** AppFunctions (Android 16+) let an agent call app actions directly — our Tier 1. Calling them needs `android.permission.EXECUTE_APP_FUNCTIONS`. AOSP history shows its protection level moving between `normal`, `internal|privileged` and `internal|role` (tied to the **Assistant role**). What actually ships on your phone?

## Run

```bash
./gradlew :spikes:b-appfunctions-access:installDebug
```

```bash
~/Library/Android/sdk/platform-tools/adb logcat -s SpikeB
```

On the phone:

1. Open **Spike B · AppFunctions** and note the JSON.
2. Tap **Request Assistant role**. Some Android builds refuse this request from apps — that is a result too.
3. If refused: **Open Default apps settings** → *Digital assistant app* → choose **Spike B**.
4. Return to the app (it refreshes on resume) and record `assistant_role_held` and `permission_granted` in [`results/phase0.md`](../../results/phase0.md).
5. Afterwards, switch the digital assistant back to your usual one.

## Reading the result

| permission_granted after holding the role | Meaning for the plan |
|---|---|
| `true` | Tier 1 works against any app that exposes AppFunctions. Next: list and call them via `AppFunctionManager`. |
| `false`, protection `internal` with a role flag | Role exists but Motorola's build doesn't grant it to us — Tier 1 only against our own demo app (or skip). |
| permission not defined | This OS build has no AppFunctions — Tier 1 is out for this device. |
