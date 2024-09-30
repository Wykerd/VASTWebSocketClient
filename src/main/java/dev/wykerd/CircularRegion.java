package dev.wykerd;


import dev.wykerd.vastproto.Messages;

public class CircularRegion implements Region {
    private final Point center;
    private final double radius;

    public CircularRegion(Point center, double radius) {
        this.center = center;
        this.radius = radius;
    }

    public Messages.CircularRegion getProto() {
        return Messages.CircularRegion.newBuilder()
                .setCenter(center.getProto())
                .setRadius(radius)
                .build();
    }
}
