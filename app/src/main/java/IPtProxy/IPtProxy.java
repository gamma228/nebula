package IPtProxy;

public class IPtProxy {

    public static final String Obfs4 = "obfs4";
    public static final String Webtunnel = "webtunnel";
    public static final String Snowflake = "snowflake";

    public static Controller newController(
        String stateDir,
        boolean enableLogging,
        boolean unsafeLogging,
        String logLevel,
        OnTransportEvents events
    ) {
        return new Controller(stateDir, enableLogging, unsafeLogging, logLevel, events);
    }
}
