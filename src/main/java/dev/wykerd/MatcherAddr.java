package dev.wykerd;

import java.net.URI;

public class MatcherAddr {
    private final String host;
    private final int port;

    public MatcherAddr(String host, int port) {
        this.host = host.toLowerCase();
        this.port = port;
    }

    public boolean equals(MatcherAddr other) {
        return this.host.equals(other.host) && this.port == other.port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public URI toURI() {
        return URI.create("ws://" + this.host + ":" + this.port);
    }
}
