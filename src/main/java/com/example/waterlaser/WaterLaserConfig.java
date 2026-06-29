package com.example.waterlaser;

/**
 * All the knobs in one place. Tweak, rebuild, done.
 */
public final class WaterLaserConfig {
    private WaterLaserConfig() {}

    // ---- Laser appearance ----------------------------------------------------
    // Colour is light blue. Values are 0..1 (red, green, blue, alpha).
    public static final float RED = 0.30f;
    public static final float GREEN = 0.70f;
    public static final float BLUE = 1.00f;
    public static final float ALPHA = 0.45f;

    // How tall the beam is, in blocks. It is not literally infinite (Minecraft
    // can't render an infinitely long quad), but 1024 blocks shoots well past
    // fog/render distance so it reads as "goes up forever".
    public static final float BEAM_HEIGHT = 1024.0f;

    // ---- Water scanning ------------------------------------------------------
    // Horizontal radius (in blocks) around you that is scanned for water.
    // Bigger = more lasers visible but more CPU work per scan.
    public static final int HORIZONTAL_RADIUS = 48;

    // How far above / below your feet to look for the water surface.
    public static final int VERTICAL_UP = 32;
    public static final int VERTICAL_DOWN = 48;

    // How often (in client ticks; 20 ticks = 1 second) the water scan re-runs.
    public static final int SCAN_INTERVAL_TICKS = 20;

    // Safety cap so a giant ocean can't spawn an unbounded number of beams.
    public static final int MAX_BEAMS = 4000;

    // Whether lasers are visible the moment you load in. The key bind toggles
    // this at runtime regardless.
    public static final boolean ENABLED_BY_DEFAULT = true;
}
