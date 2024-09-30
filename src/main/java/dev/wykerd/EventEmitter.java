package dev.wykerd;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventEmitter<E, T> {
    private final Map<E, List<Listener<T>>> listeners = new ConcurrentHashMap<>();

    public void on(E eventName, Listener<T> listener) {
        listeners.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void off(E eventName, Listener<T> listener) {
        List<Listener<T>> listenersList = listeners.get(eventName);
        if (listenersList != null) {
            listenersList.remove(listener);
        }
    }

    public void emit(E eventName, T message) {
        List<Listener<T>> listenersList = listeners.get(eventName);
        if (listenersList != null) {
            for (Listener<T> listener : listenersList) {
                listener.onEvent(message);
            }
        }
    }
}
