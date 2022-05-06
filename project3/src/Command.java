public class Command {
    private String command;
    private String msg;
    private String ip;
    private long timestamp;
    private int id;
    private int port;

    /**
     * For msend
     * @param id - id of the client
     * @param command - action the client want to take (msend)
     * @param msg - the message to send to other participant
     */
    public Command(int id, String command, String msg) {
        this.command = command;
        this.msg = msg;
        this.id = id;
        this.timestamp = System.currentTimeMillis();
        this.port = 0;
        this.ip = "";
    }

    /**
     * For register and reconnect
     * @param id
     * @param command
     * @param ip
     * @param port
     */
    public Command(int id, String command, String ip, int port) {
        this.command = command;
        this.msg = "";
        this.id = id;
        this.timestamp = 0;
        this.ip = ip;
        this.port = port;
    }

    /**
     * For deregister and disconnect
     * @param id - id of the client
     * @param command - action the client want to take
     */
    public Command(int id, String command) {
        this.command = command;
        this.id = id;
        this.timestamp = 0;
        this.msg = "";
        this.ip = "";
        this.port = 0;
    }

    /**
     * Get the command
     * @return command
     */
    public String getCommand() {
        return this.command;
    }

    /**
     * Get the message
     * @return message
     */
    public String getMsg() {
        return this.msg;
    }

    /**
     * Get the id of the participant that sent the message
     * @return id
     */
    public int getID() {
        return this.id;
    }

    /**
     * Get the time stamp in seconds
     * @return timestamp
     */
    public long getTime() {
        return this.timestamp / 1000; // to seconds
    }

    /**
     * Get the participant ip address
     * @return ip
     */
    public String getIP() {
        return this.ip;
    }

    /**
     * Get the port to connect to participant
     * @return port
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Put a new timestamp on message.
     * @param newTime in milliseconds
     */
    public void putTime(long newTime) {
         this.timestamp = newTime;
    }

    /**
     * Determine whether the message has expired.
     * @param timeout in milliseconds
     * @return true if the message has persistent longer than the given timeout, false otherwise
     */
    public boolean isExpired(long timeout) {
        return timeout < (System.currentTimeMillis() - this.timestamp);
    }
}
