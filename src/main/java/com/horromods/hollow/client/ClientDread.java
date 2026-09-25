package com.horromods.hollow.client;

/** Client-side mirror of the player's Dread, synced once per second. */
public final class ClientDread {
    private static volatile float dread = 0.0f;

    private ClientDread() {
    }

    public static void set(float value) {
        dread = value;
    }

    public static float get() {
        return dread;
    }
}
