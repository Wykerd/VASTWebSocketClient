package dev.wykerd;

import dev.wykerd.vastproto.Messages;

import java.util.ArrayList;

public class PolyRegion implements Region {
    private final ArrayList<Point> points;

    public PolyRegion(ArrayList<Point> points) {
        this.points = points;
    }

    public Messages.PolygonRegion getProto() {
        Messages.PolygonRegion.Builder builder = Messages.PolygonRegion.newBuilder();

        for (Point point : points) {
            builder.addPoints(point.getProto());
        }

        return builder.build();
    }
}
