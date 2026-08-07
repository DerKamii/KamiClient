package haven.bot;

import haven.*;

import static haven.OCache.posres;
import static haven.bot.CombatDistanceTool.animalDistances;
import static haven.bot.CombatDistanceTool.vehicleDistance;

// KamiClient: yoinked from Hurricane's haven.automated.CombatDistancerLite.
// One-shot variant of CombatDistanceTool — moves you to the perfect distance
// once, no window. Bind to a hotkey via Action.COMBAT_DISTANCE_LITE.
public class CombatDistancerLite implements Runnable {

    private final GameUI gui;

    public CombatDistancerLite(GameUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        tryToAutoDistance();
    }

    private void tryToAutoDistance() {
        if (gui != null && gui.map != null && gui.map.player() != null && gui.fv.current != null) {
            Double value = -1.0;

            double addedValue = 0.0;

            Gob player = gui.map.player();
            long vid = player.vehicleId();
            if (vid != 0) {
                Gob vehicle = gui.ui.sess.glob.oc.getgob(vid);
                if (vehicle != null && vehicle.getres() != null) {
                    addedValue = vehicleDistance.getOrDefault(vehicle.getres().name, 0.0);
                }
            }
            Gob enemy = getEnemy();
            if (enemy != null && enemy.getres() != null) {
                value = animalDistances.get(enemy.getres().name);
            }
            if (value != null && value > 0) {
                moveToDistance(value + addedValue);
            }
        }
    }

    private Gob getEnemy() {
        if (gui.fv.current != null) {
            long id = gui.fv.current.gobid;
            synchronized (gui.map.glob.oc) {
                for (Gob gob : gui.map.glob.oc) {
                    if (gob.id == id) {
                        return gob;
                    }
                }
            }
        }
        return null;
    }

    private void moveToDistance(double distance) {
        Gob enemy = getEnemy();
        if (enemy != null && gui.map.player() != null) {
            double angle = enemy.rc.angle(gui.map.player().rc);
            gui.map.wdgmsg("click", Coord.z, getNewCoord(enemy, distance, angle).floor(posres), 1, 0);
        } else {
            gui.msg("No visible target.", GameUI.MsgType.INFO);
        }
    }

    private Coord2d getNewCoord(Gob enemy, double distance, double angle) {
        return new Coord2d(enemy.rc.x + distance * Math.cos(angle), enemy.rc.y + distance * Math.sin(angle));
    }
}
