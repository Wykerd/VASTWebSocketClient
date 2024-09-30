package dev.wykerd;

import java.net.URI;
import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) {
        VASTWebSocketClient client = new VASTWebSocketClient(Logger.getGlobal(), new Point(0, 0), "testc", "");
        client.connect(URI.create("ws://localhost:20001/"));
        client.waitForAssignment();
        client.subscribe(new CircularRegion(new Point(0, 0), 100), "test", true);
        // wait 1 second
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        client.onPublication(message -> {
            System.out.println("Got message: " + message);
        });
        client.publish(new CircularRegion(new Point(10, 10), 10), "test", new byte[]{1, 2, 3, 4, 5});
    }
}