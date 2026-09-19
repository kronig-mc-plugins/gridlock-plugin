package net.kronig.gridlock.game;

public enum GameState {
    /** Players are in the lobby, choosing a spawn. */
    LOBBY,
    /** Spawn search and countdown are running. */
    STARTING,
    RUNNING,
    /** The ender dragon was killed. */
    WON,
    /** Someone died in hardcore mode. */
    LOST;

    public boolean isIngame() {
        return this == RUNNING || this == WON || this == LOST;
    }
}
