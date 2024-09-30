package dev.wykerd;

import dev.wykerd.Listener;
import dev.wykerd.vastproto.Messages;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public interface SPSClient {
    /**
     * Connect to the network
     * @param uri The client listener URI. Must be http or ws protocol.
     * @return Future that completes once the client is accepted by a matcher.
     */
    CompletableFuture<Boolean> connect(URI uri);

    /**
     * Disconnects the client from the network
     */
    void disconnect();

    /**
     * Check if the client is connected to the client listener
     * @return True if connected
     */
    boolean isConnected();

    /**
     * Check if the client ready to perform operations
     * @return True if ready
     */
    boolean isReady();

    /**
     * Move the client in the network
     * @param position The new position of the client
     */
    void move(Point position);

    /**
     * Create a subscription to a specific region and channel.
     * @param region The spacial region
     * @param channel The channel name
     * @param followClient Should the subscription move with the client?
     */
    void subscribe(Region region, String channel, boolean followClient);

    /**
     * Publish a message to some region
     * @param region The region to publish to
     * @param channel The channel to match
     * @param payload The payload of the publication
     */
    void publish(Region region, String channel, byte[] payload);

    /**
     * Remove an existing subscription
     * @param subscriptionId The id of the subscription to remove
     */
    void unsubscribe(String subscriptionId);

    /**
     * Clear all subscriptions.
     */
    void clearSubscriptions();

    /**
     * Listen for messages published to subscribed areas.
     * @param listener The listener to call
     */
    void onPublication(Listener<Messages.PubSubMessage> listener);

    /**
     * Remove a listener
     * @param listener The listener
     */
    void offPublication(Listener<Messages.PubSubMessage> listener);
}
