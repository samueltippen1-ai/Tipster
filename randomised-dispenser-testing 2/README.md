# Randomised Dispenser Testing (Fabric, Minecraft 26.2)

A small client-side Fabric mod for experimenting with dispenser/dropper mechanics in a **single-player** world.

## The arrangement it recognises

```
[Dispenser/Dropper] [solid block] [Dispenser/Dropper]      (in a line on the X or Z axis)
                       [lever]  <- attached to the solid block (top, bottom or either side)
```

No configuration needed. Detection is done on demand (when you pull a lever or open a container),
so there is no world scanning or per-tick cost.

## What happens

1. Open each container (right-click). The mod remembers its contents and the order you opened them in.
2. Pull the lever ON. The mod picks one occupied slot in each container so that the container you
   **inspected most recently gets a value >= the other one**, then tells you in chat what it chose.
3. In single-player, that choice is also handed to the integrated server, so the dispensers/droppers
   really do eject those slots.

Slot values: `slot index + 1`, so the top-left slot is 1 and the bottom-right is 9
(top row 1-3, middle row 4-6, bottom row 7-9): values rise towards the bottom of the grid.
Ties count as satisfying the rule (set `strictGreater` for strictly greater).

## Client side vs server side (important)

| Part | Where it runs | Notes |
|---|---|---|
| Arrangement detection | Client | Reads the client's view of the world |
| Inspection tracking (open order, contents) | Client | Container contents are only known to the client while the GUI is open, so they are snapshotted then (and refreshed every tick while open) |
| Outcome calculation + chat feedback | Client | `OutcomeEngine`, pure Java |
| **Actually changing which slot a dispenser ejects** | **Server** | The server owns the dispensing RNG. A client-only mod cannot change it. In single-player the integrated server runs in the same JVM, so one tiny mixin (`DispenserBlockEntityMixin`) lets the client hand over the chosen slot. |

Consequences:
- The mod is `environment: client`; it never loads on a dedicated server.
- On a **remote server the mod is completely inert** (a client cannot influence the real result there, so it does not pretend to).
- Set `applyToIntegratedServer` to `false` for a pure client-side "preview" with no server touchpoint.
- The mixin only acts on a block that was armed within the last 3 seconds; every other dispenser or dropper runs vanilla code.

## Build

Requirements: **JDK 25**, internet access for the first Gradle run.

```bash
# first time only (this zip does not include the wrapper jar/scripts):
gradle wrapper --gradle-version 9.5.1      # or copy gradlew, gradlew.bat and gradle/wrapper/* from Fabric's example mod

./gradlew build
```

The jar appears in `build/libs/randomised-dispenser-testing-0.1.0.jar` (not the `-sources` jar).
Try it without installing: `./gradlew runClient`.

If Gradle cannot resolve a version in `gradle.properties` (`loom_version`, `loader_version`, `fabric_version`),
take the current 26.2 values from <https://fabricmc.net/develop>.

## Install

1. Install Fabric Loader for Minecraft 26.2 (Java 25).
2. Put **Fabric API** (`0.161.0+26.2` or newer for 26.2) and this mod's jar into `.minecraft/mods`.
3. Start the game, make a single-player world, build the arrangement, fill both containers (e.g. one item per slot), open each, pull the lever.

## Config

`config/randomised_dispenser_testing.json` is created on first launch:

| Key | Default | Meaning |
|---|---|---|
| `enabled` | true | Master switch |
| `applyToIntegratedServer` | true | Make the real dispensers eject the chosen slots (single-player) |
| `chatFeedback` | true | Show the computed outcome in chat |
| `valueMode` | `SLOT` | `SLOT` = slot index + 1, `COUNT` = stack size |
| `strictGreater` | false | Require recent > other instead of >= |
| `spread` | 0.0 | 0 = all valid slot pairs equally likely; higher favours larger margins |
| `requireFullContainers` | false | Only act when all 9 slots of both containers are filled |
| `pendingOpenTimeoutTicks` | 40 | Max wait between right-click and GUI opening |
| `armTtlMillis` | 3000 | How long a chosen slot stays armed after the lever |

## How the pieces work

- **`SetupDetector`**: from a lever, finds the block it is attached to, checks that block is solid and has a
  dispenser/dropper on opposite sides along X or Z. From a container, does the reverse. A handful of block lookups.
- **`ContainerTracker`**: a right-click records a *pending* position; the next dispenser GUI claims it (crosshair
  target is the fallback if the server refused to open the GUI). Each open increments a sequence number, so
  "most recent" is an exact ordering even when you open/close both containers many times quickly. Resize
  re-inits of the same screen are ignored.
- **`OutcomeEngine`**: enumerates every (recentSlot, otherSlot) pair of occupied slots that satisfies
  `recentValue >= otherValue` and draws one at random (optionally weighted by margin). If the rule is impossible
  (every item in the other container outranks the recent one) it falls back to best-effort and says so.
- **`ForcedSlots` + mixin**: one-shot, expiring hand-off to the integrated server's `getRandomSlot`.

## Extending

- New value rules: `ContainerTracker.refresh` (how values are read) and `OutcomeEngine` (how pairs are chosen).
- Other arrangements: `SetupDetector`.
- Chat/UI changes: `Feedback`.
- Commands/HUD/keybind: add in `RandomisedDispenserClient.onInitializeClient`.

## Known limits

- Only lever *player clicks* arm an outcome (not buttons, pressure plates or redstone clocks).
- Dimension-aware, but designed for one player in one world.
- If you rearrange items after inspecting and before pulling the lever, the server falls back to vanilla for any armed slot that is now empty.
