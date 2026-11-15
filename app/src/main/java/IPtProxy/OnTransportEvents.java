package IPtProxy;

public interface OnTransportEvents {
    void connected(String name);
    void error(String name, Exception error);
    void stopped(String name, Exception error);
}
