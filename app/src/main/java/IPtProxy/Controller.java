package IPtProxy;

public class Controller {

    private final String stateDir;
    private final boolean enableLogging;
    private final boolean unsafeLogging;
    private final String logLevel;
    private final OnTransportEvents events;

    public Controller(String stateDir, boolean enableLogging, boolean unsafeLogging, String logLevel, OnTransportEvents events) {
        this.stateDir = stateDir;
        this.enableLogging = enableLogging;
        this.unsafeLogging = unsafeLogging;
        this.logLevel = logLevel;
        this.events = events;
    }

    public void start(String transport, String options) throws Exception {
        throw new UnsupportedOperationException(
            "In-process pluggable transport " + transport + " is not supported alongside sing-box core due to dual Go runtime constraints. Use Carrier Mode ('Tor via my server') or Vanilla Bridges."
        );
    }

    public void stop(String transport) {
        // no-op
    }

    public String localAddress(String transport) {
        return "127.0.0.1:0";
    }

    public void setSnowflakeIceServers(String iceServers) {}
    public void setSnowflakeBrokerUrl(String brokerUrl) {}
    public void setSnowflakeFrontDomains(String frontDomains) {}
    public void setSnowflakeAmpCacheUrl(String ampCacheUrl) {}
    public void setSnowflakeSqsUrl(String sqsUrl) {}
    public void setSnowflakeSqsCreds(String sqsCreds) {}
    public void setSnowflakeMaxPeers(long maxPeers) {}
    public void setSnowflakeMaxPeers(int maxPeers) {}
}
