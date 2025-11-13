package haven.bot;

import haven.*;

/**
 * Auto drink bot. Has to be initizalied with UI object to work.
 * Is a singleton, use getInstance() to access it.
 */
public class AutoDrink {
    private static AutoDrink instance;
    
    GameUI gui = null;
    long lastDrinkTime = 0;
    
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
        int autoDrinkThreshold = CFG.AUTO_DRINK_THRESHOLD.get();
        if (autoDrinkThreshold == 0)
            return;
        if (gob.is(GobTag.DRINKING))
            return;
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDrinkTime > CFG.AUTO_DRINK_DELAY.get()) {
            IMeter meter = gui.getIMeter("stam");
            if (meter != null) {
                double currentStamina = meter.meter(0);
                if (currentStamina >= 0 && currentStamina < (autoDrinkThreshold / 100f)) {
                    lastDrinkTime = currentTime;
                    gui.wdgmsg("act", "drink");
                }
            }
        }
    }
}
