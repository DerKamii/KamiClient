package haven.bot;

import auto.InvHelper;
import haven.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Auto drink bot. Has to be initizalied with UI object to work.
 * Is a singleton, use getInstance() to access it.
 */
public class AutoDrink {
    private static AutoDrink instance;
    
    GameUI gui = null;
    long lastDrinkTime = 0;
    // KamiClient: cached answer to "are we carrying anything drinkable", so the inventory sweep below
    // runs about once a second instead of every tick. UNKNOWN means we could not tell (items still
    // loading) - in that case we drink as if we had some, because being wrong costs one wasted action
    // while wrongly assuming empty would throttle a perfectly working autodrink.
    private static final int UNKNOWN = -1, EMPTY = 0, HAVE = 1;
    private int drinks = UNKNOWN;
    private long drinksTime = 0;

    private AutoDrink(){}
    
    public static AutoDrink getInstance() {
        if (instance == null) {
            instance = new AutoDrink();
        }
        return instance;
    }
    
    /**
     * Initizalize the bot. Not thread safe, but there's no need to make it so, because it's only called once
     * @param gui GameUI object to work with
     */
    public void init(GameUI gui) {
        this.gui = gui;
    }
    
    public void tick(Gob gob) {
        if (gui == null)
            return;
        if (!CFG.AUTO_DRINK_ENABLED.get())
            return;
        int autoDrinkThreshold = CFG.AUTO_DRINK_THRESHOLD.get();
        // ignore if threshold is unreachable
        if (autoDrinkThreshold == 0)
            return;
        // ignore if already in drinking state
        if (gob.is(GobTag.DRINKING))
            return;
        // this apparently is a rather cheap call
        long currentTime = System.currentTimeMillis();
        // ignore if the action was triggered recently, to address the network delay and avoid spamming drink actions
        if (currentTime - lastDrinkTime > CFG.AUTO_DRINK_DELAY.get()) {
            IMeter meter = gui.getIMeter("stam");
            if (meter != null) {
                double currentStamina = meter.meter(0);
                if (currentStamina >= 0 && currentStamina < (autoDrinkThreshold / 100f)) {
                    // with nothing to drink the action just fails, and at the delay above that means a
                    // failure message several times a second. Back off to the forced interval instead.
                    if (hasDrinks(currentTime) || currentTime - lastDrinkTime > CFG.AUTO_DRINK_FORCED_INTERVAL.get()) {
                        lastDrinkTime = currentTime;
                        gui.wdgmsg("act", "drink");
                    }
                }
            }
        }
    }

    /**
     * Whether we are carrying water or tea. Only ever called while stamina is already below the
     * threshold, and the result is cached for a second, so the inventory sweep stays cheap.
     */
    private boolean hasDrinks(long currentTime) {
        if (currentTime - drinksTime > 1000) {
            drinksTime = currentTime;
            drinks = countDrinks();
        }
        return drinks != EMPTY;
    }

    private int countDrinks() {
        try {
            List<WItem> items = Stream.of(
                    InvHelper.HANDS(gui).get().stream().filter(InvHelper::isBucket),
                    InvHelper.POUCHES(gui).get().stream().filter(InvHelper::isDrinkContainer),
                    InvHelper.INVENTORY(gui).get().stream().filter(InvHelper::isDrinkContainer),
                    InvHelper.BELT(gui).get().stream().filter(InvHelper::isDrinkContainer)
                ).flatMap(x -> x)
                .collect(Collectors.toList());

            for (WItem item : items) {
                ItemData.Content content = item.contains.get();
                if (content != null && (content.is(ItemData.WATER) || content.is(ItemData.TEA))) {
                    return HAVE;
                }
            }
            return EMPTY;
        } catch (Loading l) {
            // items not resolved yet - don't let that look like an empty inventory
            return UNKNOWN;
        }
    }
}
