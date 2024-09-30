package dev.wykerd;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import dev.wykerd.vastproto.Messages;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class VASTWebSocketClient implements SPSClient {
    private final Logger logger;
    private String id;
    private Point position;
    private String flags;
    private boolean connected = false;
    private boolean assigned = false;
    private Messages.Addr matcherAddr = null;
    private Messages.Vec2d matcherPos = null;
    private long matcherId = 0;
    private int subCount = 0;
    private final Set<String> subscriptions = new HashSet<>();
    private final LinkedList<VASTClientPendingSubscription> pendingSubscriptions = new LinkedList<>();
    private final EventEmitter<String, Messages.PubSubMessage> publicationEmitter = new EventEmitter<>();
    private CompletableFuture<Boolean> assignedFuture = new CompletableFuture<>();
    private WebSocketClientImpl ws;

    static class VASTClientPendingSubscription {
        private final String subscriptionId;
        private final String channelName;
        private boolean markedForRemoval;

        VASTClientPendingSubscription(String subscriptionId, String channelName) {
            this.subscriptionId = subscriptionId;
            this.channelName = channelName;
        }

        public String getSubscriptionId() {
            return subscriptionId;
        }

        public String getChannelName() {
            return channelName;
        }

        public boolean isMarkedForRemoval() {
            return markedForRemoval;
        }

        public void markForRemoval() {
            markedForRemoval = true;
        }
    }

    static class WebSocketClientImpl extends WebSocketClient {
        VASTWebSocketClient client;

        public WebSocketClientImpl(URI serverUri, VASTWebSocketClient client) {
            super(serverUri);
            this.client = client;
            this.connect();
        }

        @Override
        public void onOpen(ServerHandshake serverHandshake) {
            client.setConnected(true);
        }

        @Override
        public void onMessage(String message) {
            // no-op
            System.out.println("Unexpected non binary message: " + message);
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            try {
                Messages.VASTServerMessage message = Messages.VASTServerMessage.parseFrom(bytes);

                client.handleMessage(message);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onClose(int i, String s, boolean b) {
            client.setConnected(false);
        }

        @Override
        public void onError(Exception e) {
            // Drop the connection
            if (this.getReadyState() == ReadyState.CLOSED || this.getReadyState() == ReadyState.CLOSING) return;
            this.close();
        }
    }

    public VASTWebSocketClient(Logger logger, Point position, String clientID, String flags) {
        this.logger = logger;
        this.position = position;
        this.id = clientID;
        this.flags = flags;
    }

    protected void setConnected(boolean connected) {
        this.connected = connected;
    }

    protected void handleMessage(Messages.VASTServerMessage message) {
        switch (message.getMessageCase()) {
            case CONFIRM_MATCHER:
            {
                Messages.MatcherInfo confirmMatcher = message.getConfirmMatcher();
                this.id = confirmMatcher.getClientId();
                this.matcherId = confirmMatcher.getMatcherId();
                this.matcherAddr = confirmMatcher.getMatcherAddr();
                this.matcherPos = confirmMatcher.getMatcherPosition();
                this.assigned = true;
                this.assignedFuture.complete(true);
            }
            break;

            case ASSIGN_MATCHER:
            {
                Messages.MatcherInfo assignMatcher = message.getAssignMatcher();
                boolean isNewMatcher = this.matcherAddr == null || !this.matcherAddr.getHost().equals(assignMatcher.getMatcherAddr().getHost()) || this.matcherAddr.getPort() != assignMatcher.getMatcherAddr().getPort();

                this.id = assignMatcher.getClientId();
                this.matcherId = assignMatcher.getMatcherId();
                this.matcherAddr = assignMatcher.getMatcherAddr();
                this.matcherPos = assignMatcher.getMatcherPosition();

                if (isNewMatcher) {
                    if (!this.assignedFuture.isDone()) this.assignedFuture.complete(false);
                    this.assignedFuture = new CompletableFuture<>();
                    this.assigned = false;

                    this.connectInternal();
                }
            }
            break;

            case SUBSCRIBE_RESPONSE:
            {
                String subId = message.getSubscribeResponse();
                this.subscriptions.add(subId);
                this.handlePendingSubscription(subId);
                this.logger.info("VASTWebSocketClient: new subscription with id=" + subId);
                this.logger.info("VASTWebSocketClient: now have " + this.subscriptions.size() + " subscriptions");
            }
            break;

            case UNSUBSCRIBE_RESPONSE:
            {
                String subId = message.getUnsubscribeResponse();
                this.subscriptions.remove(subId);
                this.logger.info("VASTWebSocketClient: subscription with id=" + subId + " removed");
                this.logger.info("VASTWebSocketClient: now have " + this.subscriptions.size() + " subscriptions");
            }
            break;

            case PUBLICATION:
            {
                Messages.PubSubMessage pub = message.getPublication();
                this.publicationEmitter.emit("publication", pub);
            }
            break;
        }
    }

    // TODO: this is not the most efficient but the list should be short, so probably fine?
    private void handlePendingSubscription(String subId) {
        VASTClientPendingSubscription pendingSub = null;

        for (VASTClientPendingSubscription subscription : pendingSubscriptions) {
            if (subscription.getSubscriptionId().equals(subId)) {
                pendingSub = subscription;
                break;
            }
        }

        if (pendingSub == null) return;

        if (pendingSub.isMarkedForRemoval()) this.unsubscribe(pendingSub.getSubscriptionId());

        pendingSubscriptions.remove(pendingSub);
    }

    private void connectInternal() {
        if (this.ws.getReadyState() != ReadyState.CLOSED) this.ws.close();

        if (this.matcherAddr == null) return;

        URI uri = URI.create(String.format("ws://%s:%d/?id=%s&pos=%f,%f&options=%s", this.matcherAddr.getHost(), this.matcherAddr.getPort(), this.id, this.position.getX(), this.position.getY(), this.flags));

        this.ws = new WebSocketClientImpl(uri, this);
    }

    public void waitForAssignment() {
        while (true) {
            boolean didAssign = this.assignedFuture.join();
            if (didAssign) return;
        }
    }

    @Override
    public CompletableFuture<Boolean> connect(URI uri) {
        // add query params to the URI
        String queryParams = String.format("?id=%s&pos=%f,%f&options=%s", this.id, this.position.getX(), this.position.getY(), this.flags);
        URI newUri = URI.create(uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + uri.getPath() + queryParams);

        this.ws = new WebSocketClientImpl(newUri, this);

        return this.assignedFuture;
    }

    @Override
    public void disconnect() {
        this.assigned = false;
        this.connected = false;
        if (!this.assignedFuture.isDone()) this.assignedFuture.complete(false);
        this.subscriptions.clear();
        this.ws.close();
    }

    @Override
    public boolean isConnected() {
        return this.connected;
    }

    @Override
    public boolean isReady() {
        return this.assigned && this.connected;
    }

    @Override
    public void move(Point position) {
        this.ws.send(Messages.VASTClientMessage.newBuilder().setMove(position.getProto()).build().toByteArray());
    }

    @Override
    public void subscribe(Region region, String channel, boolean followClient) {
        String subscriptionId = "vc:" + this.id + ":" + this.subCount++;

        Messages.Subscribe.Builder subscribeBuilder = Messages.Subscribe.newBuilder()
                .setChannel(channel)
                .setFollow(followClient)
                .setId(subscriptionId);

        if (region instanceof PolyRegion) {
            Messages.PolygonRegion polygonRegion = ((PolyRegion) region).getProto();
            subscribeBuilder.setPolygon(polygonRegion);
        } else if (region instanceof CircularRegion) {
            Messages.CircularRegion circleRegion = ((CircularRegion) region).getProto();
            subscribeBuilder.setCircular(circleRegion);
        }

        this.ws.send(Messages.VASTClientMessage.newBuilder().setSubscribe(subscribeBuilder.build()).build().toByteArray());

        pendingSubscriptions.add(new VASTClientPendingSubscription(this.id, channel));
    }

    @Override
    public void publish(Region region, String channel, byte[] payload) {
        Messages.PubSubMessage.Builder builder = Messages.PubSubMessage.newBuilder()
                .setChannel(channel)
                .setPayload(ByteString.copyFrom(payload));

        if (region instanceof PolyRegion) {
            Messages.PolygonRegion polygonRegion = ((PolyRegion) region).getProto();
            builder.setPolygon(polygonRegion);
        } else if (region instanceof CircularRegion) {
            Messages.CircularRegion circleRegion = ((CircularRegion) region).getProto();
            builder.setCircular(circleRegion);
        }

        this.ws.send(Messages.VASTClientMessage.newBuilder().setPublish(builder.build()).build().toByteArray());
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        this.ws.send(Messages.VASTClientMessage.newBuilder().setUnsubscribe(subscriptionId).build().toByteArray());
    }

    @Override
    public void clearSubscriptions() {
        for (String subscription : subscriptions) {
            this.unsubscribe(subscription);
        }

        for (VASTClientPendingSubscription subscription : pendingSubscriptions) {
            subscription.markForRemoval();
        }
    }

    @Override
    public void onPublication(Listener<Messages.PubSubMessage> listener) {
        this.publicationEmitter.on("publication", listener);
    }

    @Override
    public void offPublication(Listener<Messages.PubSubMessage> listener) {
        this.publicationEmitter.off("publication", listener);
    }
}
