package me.ender;

import haven.*;
import me.ender.minimap.SMarker;

import java.awt.*;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static haven.MCache.*;

public class QuestCondition implements Comparable<QuestCondition> {
    private final GameUI gui;
    private static final Pattern pat = Pattern.compile("(Tell|Greet| to| at) (\\w+)");
    private String questGiver = "";
    private SMarker questGiverMarker;
    private final String questTitle;
    private final String searchDescription;
    private boolean isEndpoint;
    private boolean isLast;

    public int questId;
    public String description;
    public boolean isCurrent = false;

    public QuestCondition(String description, boolean isEndpoint, boolean isLast, int questId, String questTitle, GameUI gui) {
	this.questId = questId;
	this.description = description;
	this.isEndpoint = isEndpoint;
	this.isLast = isLast;
	this.questTitle = questTitle;
	this.gui = gui;

	this.questGiver = parseQuestGiver(description);

	int i = description.lastIndexOf('(');
	searchDescription = i > 0 ? description.substring(0, i - 1) : description;

	addMarker();
    }

    public void update(String description, boolean isEndpoint, boolean isLast)
    {
	this.isEndpoint = isEndpoint;
	this.isLast = isLast;
	this.description = description;

	/* KamiClient: re-read the quest giver out of the new text. Conditions are matched
	 * by id + description-without-counter, so one of these objects gets reused as the
	 * objective changes - and questGiver used to be parsed once in the constructor and
	 * never again. A quest that moved you from Gerberga to Gerberg kept pointing at
	 * Gerberga forever, because both the name and the marker were stuck on the first
	 * value they ever had. */
	String giver = parseQuestGiver(description);
	if(!Objects.equals(giver, questGiver)) {
	    removeMarker();
	    questGiver = giver;
	    questGiverMarker = null;
	}

	addMarker();
    }

    public void removeMarker()
    {
	if (questGiverMarker != null)
	    questGiverMarker.questConditions.remove(this);
    }

    public String name() {
	if(isCredo()) return "\uD83D\uDD6E " + description;
	else if(isLast) return "✓ " + description;
	else return description;
    }

    public Color color() {
	return isLast 
	    ? isCurrent ? Color.CYAN : Color.GREEN 
	    : isCurrent ? Color.WHITE : Color.LIGHT_GRAY;
    }

    public Color questGiverMarkerColor() {
	if(isEndpoint) {
	    if(isLast) return Color.GREEN;
	    else return Color.YELLOW;
	} else return Color.WHITE;
    }
    
    public String distance() {
	if(questGiver == null || gui == null || gui.map == null || gui.mapfile == null) {return null;}

	MiniMap.Location loc = gui.mapfile.playerLocation();
	if(loc == null) {return null;}

	Gob player = gui.map.player();
	if(player == null) {return null;}

	Coord2d pc = player.rc;
	Coord tc = null;

	if(questGiverMarker != null)
	    if(questGiverMarker.seg == loc.seg.id) {tc = questGiverMarker.tc.sub(loc.tc);}

	if(tc == null) {return null;}

	return String.format("%.0fm", tc.sub(pc.floor(tilesz)).abs());
    }

    public boolean Equals(int questId, String questDescription) {
	int i = questDescription.lastIndexOf('(');
	String searchDescription = i > 0 ? questDescription.substring(0, i - 1) : questDescription;
	return this.questId == questId && Objects.equals(this.searchDescription, searchDescription);
    }

    public int compareTo(QuestCondition o) {
	int result = -Boolean.compare(isCurrent, o.isCurrent);
	if(result != 0) {return result;}

	result = -Boolean.compare(isCredo(), o.isCredo());
	if(result != 0) {return result;}
	
	result = Boolean.compare(isLast, o.isLast);
	if(result != 0) {return CFG.QUESTHELPER_DONE_FIRST.get() ? -result : result;}


	result = -Boolean.compare(questTitle != null, o.questTitle != null);
	if(result != 0) {return result;}

	return description.compareTo(o.description);
    }

    private boolean isCredo() {return gui.chrwdg != null && gui.chrwdg.skill != null && gui.chrwdg.skill.credos != null && gui.chrwdg.skill.credos.pqid == questId;}

    private static String parseQuestGiver(String description)
    {
	Matcher matcher = pat.matcher(description);
	return matcher.find() ? matcher.group(2) : "";
    }

    private void addMarker()
    {
	if(questGiverMarker == null && !questGiver.isEmpty() && gui.mapfile != null) {
	    questGiverMarker = gui.mapfile.findMarker(questGiver);
	}
	if (questGiverMarker != null) {
	    if (!questGiverMarker.questConditions.contains(this)) {
		questGiverMarker.questConditions.add(this);
	    }
	}
    }
}