# Water Laser

A client-side Fabric mod for **Minecraft 1.21.11**. It finds water around you and
shoots an infinite, block-wide, light-blue laser straight up out of each water
surface. A key bind in **Options → Controls → Water Laser** toggles the lasers on
and off.

## What it does

- Scans the loaded area around you for water (sources, flowing water, and
  waterlogged blocks all count).
- For the top water block in each column, draws a 1×1 translucent blue column that
  shoots up ~1024 blocks (past fog/render distance, so it reads as "infinite").
- Adds a toggle key bind (default **L**) that appears in the vanilla Controls
  screen, so you can rebind it like any other key.

Because Minecraft's client only knows about chunks loaded near you, "every water on
the server" in practice means every water block within a configurable radius around
the player. Tune that radius in `WaterLaserConfig`.

## Getting the finished mod jar

This folder is **source code**. A Fabric mod has to be compiled once before it
becomes a drop-in `.jar`. Pick whichever route is easiest for you.

### Option A — Let GitHub build it (no installing anything)

1. Make a free account at https://github.com and create a new **empty repository**.
2. Upload the contents of this folder to it (drag-and-drop in the browser works, or
   use GitHub Desktop). Keep the folder layout intact.
3. Go to the repo's **Actions** tab. The "Build Water Laser mod" workflow runs
   automatically (or open it and press **Run workflow**).
4. When the green check appears, open that run and download the **waterlaser-mod**
   artifact at the bottom. Inside is your finished `waterlaser-1.0.0.jar`.

GitHub's servers download Minecraft + Fabric for you, so you need no local dev setup.

### Option B — Build it locally with IntelliJ IDEA (free)

1. Install **IntelliJ IDEA Community Edition** (it bundles Java + Gradle).
2. `File → Open` this folder and let it import (this downloads Minecraft + Fabric).
3. In the Gradle panel (right side), run **Tasks → build → build**.
4. The finished jar is in `build/libs/waterlaser-1.0.0.jar` (ignore `-sources.jar`).

### Option C — Command line (if you have Gradle + JDK 21 installed)

```
gradle build
```
Finished jar: `build/libs/waterlaser-1.0.0.jar`.

## Install

1. Install **Fabric Loader 0.18.1+** for Minecraft 1.21.11.
2. Put **Fabric API `0.141.3+1.21.11`** in your `mods` folder.
3. Put `waterlaser-1.0.0.jar` in your `mods` folder.
4. Launch. Press **L** (or rebind it in Options → Controls → Water Laser) to toggle.

## Tweaking

Everything lives in `WaterLaserConfig.java`:

- `RED` / `GREEN` / `BLUE` / `ALPHA` — the laser colour (currently light blue).
- `BEAM_HEIGHT` — how tall the beam is.
- `HORIZONTAL_RADIUS`, `VERTICAL_UP`, `VERTICAL_DOWN` — the scan box.
- `SCAN_INTERVAL_TICKS` — how often it rescans (20 = once per second).
- `MAX_BEAMS` — hard cap so an ocean can't tank your FPS.
- `ENABLED_BY_DEFAULT` — whether lasers show immediately on world load.

Want the lasers visible **through walls** (x-ray style)? In `LaserRenderer`, swap the
`PIPELINE` for a custom one based on `RenderPipelines.DEBUG_FILLED_SNIPPET` with
`DepthTestFunction.NO_DEPTH_TEST` — see the Fabric "Rendering in the World" guide.

## Notes

- Built against Mojang official mappings (1.21.11 renamed `ResourceLocation` to
  `Identifier`, which is why that name appears in the code).
- Client-only; safe to use when joining vanilla or modded servers since it never
  talks to the server.
