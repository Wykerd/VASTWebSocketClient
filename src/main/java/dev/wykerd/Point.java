package dev.wykerd;

import dev.wykerd.vastproto.Messages;

public class Point {
    private final double x;
    private final double y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public Messages.Vec2d getProto() {
        return Messages.Vec2d.newBuilder().setX(x).setY(y).build();
    }
}
