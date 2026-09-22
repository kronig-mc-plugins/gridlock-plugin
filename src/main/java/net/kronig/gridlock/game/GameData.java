package net.kronig.gridlock.game;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything that survives a restart. Serialized to data.json with Gson. */
public final class GameData {

    public GameState state = GameState.LOBBY;
    public String preset;

    public long timerSeconds;
    public boolean timerPaused;
    public long secondsSinceTimeLevel;

    public Map<String, Long> playtime = new HashMap<>();
    public Set<String> initializedPlayers = new HashSet<>();
    public Map<String, String> votes = new HashMap<>();
    /** Players who switched their own scoreboard off. */
    public Set<String> hiddenSidebar = new HashSet<>();

    public int poolLevels;
    public int poolPoints;

    /** World name -> packed columns (see FieldManager#pack). */
    public Map<String, List<Long>> fields = new HashMap<>();
    /** World name -> the column the field of that world started with. */
    public Map<String, Long> fieldOrigins = new HashMap<>();

    public String spawnWorld;
    public double spawnX;
    public double spawnY;
    public double spawnZ;

    public String finishInfo;

    // ---- round statistics (player UUID -> value)
    public Map<String, Integer> blocksBought = new HashMap<>();
    public Map<String, Integer> deaths = new HashMap<>();
    public Map<String, Integer> levelsSpent = new HashMap<>();
    public int blocksSold;
    /** Buyback credit in hundredths of a level (fractions below one level), player UUID or "pool". */
    public Map<String, Integer> refundCredit = new HashMap<>();
    public long roundStartedEpoch;

    // ---- bonus events (times in challenge-timer seconds)
    public String activeBonus;
    public long bonusEndsAt;
    public long nextBonusAt;
    public Set<String> freeBlockUsed = new HashSet<>();

    // ---- homes
    public Map<String, SavedLocation> homes = new HashMap<>();
    public Map<String, Long> lastHomeUse = new HashMap<>();

    public static final class SavedLocation {
        public String world;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
    }
}
