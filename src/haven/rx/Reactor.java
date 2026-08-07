package haven.rx;

import haven.FlowerMenu;
import haven.Gob;
import haven.Pair;
import haven.Window;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;

public class Reactor {
    /** Publishes all 'error' messages from server*/
    public static final PublishSubject<String> EMSG = PublishSubject.create();

    /** Publishes all 'info' messages from server*/
    public static final PublishSubject<String> IMSG = PublishSubject.create();
    
    /** Publishes changes to player name. BehaviorSubject so late subscribers
     *  get the current value at subscribe-time. */
    public static final BehaviorSubject<String> PLAYER = BehaviorSubject.create();
 
    public static final PublishSubject<FlowerMenu> FLOWER = PublishSubject.create();
    
    public static final PublishSubject<FlowerMenu.Choice> FLOWER_CHOICE = PublishSubject.create();
    
    /** Publishes window events */
    public static final PublishSubject<Pair<Window, String>> WINDOW = PublishSubject.create();
    
    /** Publishes various events */
    public final static PublishSubject<Event> EVENTS = PublishSubject.create();
    /** Publishes right-clicks on gobs */
    public final static PublishSubject<Gob> GOB_INTERACT = PublishSubject.create();
    
    public static void event(String name) {
        EVENTS.onNext(new Event(name));
    }
    
    public static void event(String name, Object data) {
        EVENTS.onNext(new Event(name, data));
    }
    
    public static Subscription listen(String event, Action1<Event> callback) {
        return EVENTS.filter(e -> e.name.equals(event)).subscribe(callback);
    }
    
    public static Subscription listen(String event, Action0 callback) {
        return EVENTS.filter(e -> e.name.equals(event)).subscribe(e -> callback.call());
    }
    
    public static <T> Subscription listen(String event, Action1<T> callback, Class<T> clazz) {
        return EVENTS.filter(e -> e.name.equals(event)).subscribe(e -> {
            T data = null;
            try {data = clazz.cast(e.data);} catch (ClassCastException ignored) {}
            callback.call(data);
        });
    }
    
    public static class Event {
        public final String name;
        public final Object data;
        
        Event(String name) {
            this(name, null);
        }
        
        Event(String name, Object data) {
            this.name = name;
            this.data = data;
        }
    }
}
