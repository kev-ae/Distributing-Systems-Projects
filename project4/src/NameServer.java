import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NameServer {
    private String IP;
    private String predecessorIP;
    private int predecessorPort;
    private int predID;
    private String successorIP;
    private int successorPort;
    private int succID;
    private int id;
    private int[] range = {-1, -1};
    private int listeningPort;
    private String bootIP;
    private int bootPort;
    private boolean isInSystem;
    private boolean preIsBoot;
    private boolean sucIsBoot;
    private ConcurrentHashMap<Integer, String> table;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private SelectionKey serverKey;

    public static void main(String[] args) {
        // Ensure config file include
        if (args.length != 1) {
            System.err.println("Argument Error: Expected 'java BootStrap [config file]'");
            System.exit(1);
        }

        try {
            File config = new File(args[0]);
            Scanner scanner = new Scanner(config);
            int id = Integer.parseInt(scanner.nextLine().trim());
            int listeningPort = Integer.parseInt(scanner.nextLine().trim());
            String[] s = scanner.nextLine().trim().split(" ");
            String bootIP = s[0];
            int bootPort = Integer.parseInt(s[1]);

            scanner.close();
            NameServer ns = new NameServer(id, bootIP, bootPort, listeningPort);
            ns.initialized();
            ns.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    } // end main

    public NameServer(int id, String bootIP, int bootPort, int listeningPort) throws IOException {
        this.table = new ConcurrentHashMap<>();
        this.IP = InetAddress.getLocalHost().getHostAddress();
        this.id = id;
        this.bootIP = bootIP;
        this.bootPort = bootPort;
        this.isInSystem = false;
        this.preIsBoot = false;
        this.sucIsBoot = false;
        this.listeningPort = listeningPort;
        InetAddress lh = InetAddress.getLocalHost();
        InetSocketAddress listen = new InetSocketAddress(lh, listeningPort);
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverKey = serverChannel.register(this.selector = Selector.open(), SelectionKey.OP_ACCEPT);
        this.serverChannel.bind(listen);
    }

    public void enter() throws UnknownHostException, IOException {
        Socket bootStrap = new Socket(bootIP, bootPort);
        PrintWriter out = new PrintWriter(bootStrap.getOutputStream(), true);
        out.println("enter:" + this.id + ":" + this.IP + ":" + this.listeningPort);
        // wait for bootstrap response
        BufferedReader reader = new BufferedReader(new InputStreamReader(bootStrap.getInputStream()));
        String results;
        results = reader.readLine();
        // [predecessorIP, predecessorPort, successorIP, successorPort, contactedServers]
        String[] nodes = results.split(":");
        if (nodes[0].equalsIgnoreCase("bootstrap")) {
            if (nodes[2].equalsIgnoreCase("bootstrap")) {
                this.preIsBoot = true;
                this.sucIsBoot = true;
                this.predecessorIP = "bootstrap";
                this.predecessorPort = 0;
                this.successorIP = "bootstrap";
                this.successorPort = 0;
            } else {
                this.preIsBoot = true;
                this.predecessorIP = "bootstrap";
                this.predecessorPort = 0;
                this.successorIP = nodes[2];
                this.successorPort = Integer.parseInt(nodes[3]);
            }
        } else if (nodes[2].equalsIgnoreCase("bootstrap")) {
            this.sucIsBoot = true;
            this.successorIP = "bootstrap";
            this.successorPort = 0;
            this.predecessorIP = nodes[0];
            this.predecessorPort = Integer.parseInt(nodes[1]);
        } else {
            this.predecessorIP = nodes[0];
            this.predecessorPort = Integer.parseInt(nodes[1]);
            this.successorIP = nodes[2];
            this.successorPort = Integer.parseInt(nodes[3]);
        }

        if (preIsBoot && sucIsBoot) {
            this.predID = 0;
            this.succID = 0;
        } else {
            String[] servers = nodes[4].split("->");
            this.predID = Integer.parseInt(servers[servers.length - 1]);
            this.succID = Integer.parseInt(servers[servers.length - 2]);
        }
        this.isInSystem = true;
        System.out.println("Successful entry | Range: ["
                + range[0] + "," + range[1]
                + "] | Successor: " + this.predID
                + " Predeccessor: " + this.succID
                + " | Contacted Servers: " + nodes[4]);
        out.close();
        reader.close();
        bootStrap.close();
    }

    public void exit() {
        // [exit, ip, successor/predecessor, newIP, newPort, lowerrange, key#values]
        try {
            String msg;

            // send to successor the values and tell it to connect to new predecessor
            msg = "exit:" + this.predID + ":predecessor:" + this.predecessorIP + ":" + this.predecessorPort + ":" + range[0] + ":";
            String ret;
            for (int i = range[0]; i < range[1]; i++) {
                if ((ret = table.remove(i)) != null) {
                    msg += i + "#" + ret + ":";
                }
            }
            if ((ret = table.remove(range[1])) != null) {
                msg += range[1] + "#" + ret;
            }

            Socket temp;
            if (sucIsBoot) {
                temp = new Socket(this.bootIP, this.bootPort);
            } else {
                temp = new Socket(this.successorIP, this.successorPort);
            }
            PrintWriter out = new PrintWriter(temp.getOutputStream(), true);
            out.println(msg);
            
            temp.setSoTimeout(20000); // set 20 sec timeout, if acknowledge is not recieve, throw exception
            try {
                while(temp.getInputStream().read() >= 0) {}
            } catch (SocketTimeoutException ste) {
                System.out.println("Failed to transfer values to a name server");
            }

            out.close();
            temp.close();

            // send to predecessor and tell it to connect to new successor
            msg = "exit:" + this.succID + ":successor:" + this.successorIP + ":" + this.successorPort + ":";
            sendToPredecessor(msg);

            System.out.println("> Successful exit. SuccessorID=" + succID + " Key Range Handed Over=[" + range[0] + "," + range[1] + "]");
            // reset variables
            this.isInSystem = false;
            this.predecessorIP = null;
            this.predecessorPort = 0;
            this.predID = -1;
            this.preIsBoot = false;
            this.successorIP = null;
            this.successorPort = 0;
            this.succID = -1;
            this.sucIsBoot = false;
            this.range[0] = -1;
            this.range[1] = -1;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() throws UnknownHostException, IOException {
        Scanner scanner = new Scanner(System.in);
        boolean loop = true;
        String command;
        while (loop) {
            System.out.print("> ");
            command = scanner.nextLine();
            command = command.trim();

            switch (command.toLowerCase()) {
                case "enter":
                    if (!isInSystem) {
                        enter();
                    } else {
                        System.out.println("You are already in the system");
                    }
                    break;
                case "exit":
                    if (isInSystem) {
                        exit();
                    } else {
                        System.out.println("You need to be in the system to exit it");
                    }
                    break;
                case "quit":
                    if (!isInSystem) {
                        loop = false;
                    } else {
                        System.out.println("You must exit the system first before you can quit");
                    }
                    break;
                default:
                    System.out.println("Not a valid command");
                    break;
            }
        }
        scanner.close();
        System.exit(0);
    }

    private void sendToSuccessor(String msg) {
        try {
            if (sucIsBoot) {
                sendToBootStrap(msg);
            } else {
                Socket sock = new Socket(successorIP, successorPort);
                PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
                out.println(msg + "->" + this.id);
                out.close();
                sock.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendToPredecessor(String msg) {
        try {
            if (preIsBoot) {
                sendToBootStrap(msg);
            } else {
                Socket sock = new Socket(predecessorIP, predecessorPort);
                PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
                out.println(msg + "->" + this.id);
                out.close();
                sock.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendToBootStrap(String msg) {
        try {
            Socket sock = new Socket(bootIP, bootPort);
            PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
            out.println(msg + "->" + this.id);
            out.close();
            sock.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void lookUp(String[] msg) {
        // [lookup, key, contactedServers]
        if (checkInRange(Integer.parseInt(msg[1]))) {
            String value = table.get(Integer.parseInt(msg[1]));
            String ret;
            if (value == null) {
                ret = "lookup:Key not found:" + msg[2];
            } else {
                ret = "lookup:key=" + msg[1] + " value=" + value + ":" + msg[2];
            }
            sendToBootStrap(ret);
        } else {
            String ret = String.join(":", msg);
            if (sucIsBoot) {
                ret = "lookup:Key not found:" + msg[2];
            }
            sendToSuccessor(ret);
        }
    }

    private void insert(String[] msg) {
        // [insert, key, value, contactedServers]
        if (checkInRange(Integer.parseInt(msg[1]))) {
            table.put(Integer.parseInt(msg[1]), msg[2]);
            String ret = "insert:" + msg[1] + ":" + msg[2] + ":" + this.id + ":" + msg[3];
            sendToBootStrap(ret);
        } else {
            sendToSuccessor(String.join(":", msg));
        }
    }

    private void delete(String[] msg) {
        // [delete, key, contactedServers]
        if (checkInRange(Integer.parseInt(msg[1]))) {
            String value = table.remove(Integer.parseInt(msg[1]));
            String ret;
            if (value == null) {
                ret = "delete: Key not found:" + msg[2];
            } else {
                ret = "delete: Successful deletion:" + msg[2];
            }
            sendToBootStrap(ret);
        } else {
            String ret = String.join(":", msg);
            if (sucIsBoot) {
                ret = "delete: Key not found:" + msg[2];
            }
            sendToSuccessor(ret);
        }
    }

    private void incomingEnter(String[] msg) {
        // enter:id:ipaddr:port:contactedServers
        try {
            int id = Integer.parseInt(msg[1]);
            if (checkInRange(id)) {
                // [enterConnected, id, newIP, newPort, successorIPaddr, successorPort, lowerRange, upperRange, contactedServers]
                int[] oldRange = {this.range[0], id};
                range[0] = id + 1;
                String mess = "enterConnected:" 
                + msg[1] + ":"
                + msg[2] + ":" 
                + msg[3] + ":"
                + this.IP + ":"
                + this.listeningPort + ":" 
                + oldRange[0] + ":" 
                + oldRange[1] + ":"
                + msg[4];

                // send over old values
                transferValues(msg[2], Integer.parseInt(msg[3]), oldRange[0], oldRange[1]);

                // send enterConnected to old Predecessor
                sendToPredecessor(mess);
                this.predecessorIP = msg[2];
                this.predecessorPort = Integer.parseInt(msg[3]);
                this.preIsBoot = false;
                this.predID = id;
            } else {
                sendToSuccessor(String.join(":", msg));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void incomingExit(String[] msg, SocketChannel chan) {
        // [exit, id, successor/predecessor, newIP, newPort, lowerrange, key#values]
        // get the range and transfer values
        if (msg[2].equalsIgnoreCase("predecessor")) {
            try {
                // set the new range
                this.range[0] = Integer.parseInt(msg[5]);

                // get the new values
                String[] values;
                if (msg.length > 6) {
                    for (int i = 6; i < msg.length; i++) {
                        values = msg[i].split("#");
                        this.table.put(Integer.parseInt(values[0]), values[1]);
                    }
                }
                chan.write(ByteBuffer.allocate(8).putInt(1)); // send back acknowledgement

                if (msg[3].equalsIgnoreCase("bootstrap")) {
                    this.predecessorIP = "bootstrap";
                    this.predecessorPort = 0;
                    this.preIsBoot = true;
                    this.predID = 0;
                } else {
                    this.predecessorIP = msg[3];
                    this.predecessorPort = Integer.parseInt(msg[4]);
                    this.preIsBoot = false;
                    this.predID = Integer.parseInt(msg[1]);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (msg[2].equalsIgnoreCase("successor")) {
            if (msg[3].equalsIgnoreCase("bootstrap")) {
                this.successorIP = "bootstrap";
                this.successorPort = 0;
                this.sucIsBoot = true;
                this.succID = 0;
            } else {
                this.successorIP = msg[3];
                this.successorPort = Integer.parseInt(msg[4]);
                this.sucIsBoot = false;
                this.succID = Integer.parseInt(msg[1]);
            }
        }
    }

    private boolean checkInRange(int id) {
        if (range[0] <= id && id <= range[1]) {
            return true;
        }
        return false;
    }

    private void transferValues(String ip, int port, int lower, int upper) {
        try {
            String values = "values:" + lower + "#" + upper + ":";
            String ret;
            for (int i = lower; i < upper; i++) {
                if ((ret = table.remove(i)) != null) {
                    values += i + "#" + ret + ":";
                }
            }

            if ((ret = table.remove(upper)) != null) {
                values += upper + "#" + ret;
            }
            Socket temp = new Socket(ip, port);
            PrintWriter out = new PrintWriter(temp.getOutputStream(), true);
            out.println(values);
            
            temp.setSoTimeout(20000); // set 20 sec timeout, if acknowledge is not recieve, throw exception
            try {
                while(temp.getInputStream().read() >= 0) {}
            } catch (SocketTimeoutException ste) {
                System.out.println("Failed to transfer values to a name server");
            }

            out.close();
            temp.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initialized() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                selector.selectNow();

                for(SelectionKey key : selector.selectedKeys()) {
                    if (!key.isValid()) {
                        continue;
                    }

                    // new connections
                    if (key == serverKey) {
                        SocketChannel chan = serverChannel.accept();
                        if (chan == null) continue;

                        chan = (SocketChannel) chan.configureBlocking(false);
                        Connection nameServer = new Connection(chan);
                        
                        // read the msg and parse it
                        String msg = nameServer.read();
                        String[] msgArgs = msg.trim().split(":");
                        
                        switch(msgArgs[0]) {
                            case "lookup":
                                lookUp(msgArgs);
                                break;
                            case "insert":
                                insert(msgArgs);
                                break;
                            case "delete":
                                delete(msgArgs);
                                break;
                            case "enter":
                                incomingEnter(msgArgs);
                                break;
                            case "exit":
                                // [exit, ip, successor/predecessor, newIP, newPort, lowerrange, key#values]
                                incomingExit(msgArgs, chan);
                                break;
                            case "values":
                                String[] messArgs = msgArgs[1].split("#");
                                String[] values;

                                // assign range
                                this.range[0] = Integer.parseInt(messArgs[0]);
                                this.range[1] = Integer.parseInt(messArgs[1]);

                                if (msgArgs.length > 2) {
                                    for(int i = 2; i < msgArgs.length; i++) {
                                        values = msgArgs[i].split("#");
                                        this.table.put(Integer.parseInt(values[0]), values[1]);
                                    }
                                }
                                chan.write(ByteBuffer.allocate(8).putInt(1)); // send back acknowledgement
                                break;
                            case "enterConnected":
                                // [enterConnected, id, newIP, newPort, successorIPaddr, successorPort, lowerRange, upperRange, contactedServers]
                                String ret = "enterReturn:"
                                        + msgArgs[2] + ":"
                                        + this.IP + ":"
                                        + this.listeningPort + ":"
                                        + msgArgs[4] + ":"
                                        + msgArgs[5] + ":"
                                        + msgArgs[6] + ":"
                                        + msgArgs[7] + ":"
                                        + msgArgs[8];
                                this.successorIP = msgArgs[2];
                                this.successorPort = Integer.parseInt(msgArgs[3]);
                                this.sucIsBoot = false;
                                this.succID = Integer.parseInt(msgArgs[1]);
                                sendToBootStrap(ret);
                                break;
                        }
                        nameServer.close();
                    }
                }
                selector.selectedKeys().clear();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }
}