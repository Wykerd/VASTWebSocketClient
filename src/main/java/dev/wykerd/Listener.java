package dev.wykerd;

public interface Listener<T> {
    void onEvent(T message);
}
