import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Scanner;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BootStrap {
    private String successorIP;
    private int successorPort;
    private int sucID;
    private String predecessorIP;
    private int predecessorPort;
    private int preID;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private SelectionKey serverKey;
    private int id;
    private int port;
    private int[] range;
    private ConcurrentHashMap<Integer, String> table;
    private ConcurrentHashMap<String, Connection> ipToConnectionMap;

    /**
     * Main method, which parses the config and starts the program.
     */
    public static void main(String[] args) {
        // Ensure config file include
        if (args.length != 1) {
            System.err.println("Argument Error: Expected 'java BootStrap [config file]'");
            System.exit(1);
        }
        // Read the config file
        try {
            File config = new File(args[0]);
            Scanner scanner = new Scanner(config);
            ConcurrentHashMap<Integer, String> initialValues = new ConcurrentHashMap<>();
            String[] s;
            int id = Integer.parseInt(scanner.nextLine().trim());
            int port = Integer.parseInt(scanner.nextLine().trim());

            while (scanner.hasNextLine()) {
                s = scanner.nextLine().trim().split(" ");
                initialValues.put(Integer.parseInt(s[0]), s[1]);
            }
            scanner.close();

            BootStrap bs = new BootStrap(id, port, initialValues);
            bs.initialized();
            bs.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    } // end main

    public BootStrap(int id, int port, ConcurrentHashMap<Integer, String> initialValues) throws IOException {
        this.id = id;
        this.port = port;
        this.range = new int[]{0, 1023};
        this.table = initialValues;
        InetAddress lh = InetAddress.getLocalHost();
        InetSocketAddress listenPort = new InetSocketAddress(lh, port);
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverKey = serverChannel.register(this.selector = Selector.open(), SelectionKey.OP_ACCEPT);
        this.serverChannel.bind(listenPort);
        this.ipToConnectionMap = new ConcurrentHashMap<>();
    }

    public void lookUp(String key) {
        int k = Integer.parseInt(key);
        if (checkInRange(k)) {
            String value = table.get(k);
            if (value == null) {
                printResult("Lookup Results: Key not found", "0");
            } else {
                printResult("Lookup Results: " + "key=" + key + " value=" + value, "0");
            }
        } else {
            sendToSuccessor("lookup:" + key + ":0");
        } // if/else
    } // lookUp

    public void insert(String key, String value) {
        int k = Integer.parseInt(key);
        if (checkInRange(k)) {
            table.put(k, value);
            printResult("Insert Results: Pair " + key + "," + value + " store in server = 0", "0");
        } else {
            sendToSuccessor("insert:" + key + ":" + value + ":0");
        } // if/else
    }

    public void delete(String key) {
        int k = Integer.parseInt(key);
        if (checkInRange(k)) {
            String value = table.remove(k);
            if (value == null) {
                printResult("Delete Results: Key not found", "0");
            } else {
                printResult("Delete Results: " + "delete: Successful deletion:", "0");
            }
        } else {
            sendToSuccessor("delete:" + key + ":0");
        } // if/else
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
                        String mess;
                        Connection server;
                        
                        switch(msgArgs[0]) {
                            case "lookup":
                                // [lookup, results, contactedServers]
                                printResult("Lookup Results: " + msgArgs[1], msgArgs[2]);
                                nameServer.close();
                                break;
                            case "insert":
                                // [insert, key, value, serverid, contactedServers]
                                printResult("Insert Results: Pair=(" + msgArgs[1] + "," + msgArgs[2] + ") Store In ServerID=" + msgArgs[3], msgArgs[4]);
                                nameServer.close();
                                break;
                            case "delete":
                                // [delete, result, contactedServers]
                                printResult("Delete Results: " + msgArgs[1], msgArgs[2]);
                                nameServer.close();
                                break;
                            case "enter":
                                // [enter, id, ipaddress, port]
                                nameServer.setIP(msgArgs[2]);
                                ipToConnectionMap.put(nameServer.getIP(), nameServer);

                                // if bootstrap is only one
                                if (range[0] == 0 && range[1] == 1023) {
                                    int id = Integer.parseInt(msgArgs[1]);
                                    int[] oldRange = {this.range[0], id};
                                    range[0] = id + 1;
                                    mess = "bootstrap::bootstrap::0";

                                    // assign pre and succ
                                    successorIP = nameServer.getIP();
                                    successorPort = Integer.parseInt(msgArgs[3]);
                                    sucID = id;
                                    predecessorIP = successorIP;
                                    predecessorPort = successorPort;
                                    preID = id;

                                    // transfer old values
                                    transferValues(predecessorIP, predecessorPort, oldRange[0], oldRange[1]);
                                    ipToConnectionMap.remove(nameServer.getIP());
                                    nameServer.write(mess);
                                    nameServer.close();
                                } else {
                                    int id = Integer.parseInt(msgArgs[1]);
                                    if (checkInRange(id)) {
                                        int[] oldRange = {this.range[0], id};
                                        range[0] = id + 1;

                                        // transfer old values
                                        transferValues(nameServer.getIP(), Integer.parseInt(msgArgs[3]), oldRange[0], oldRange[1]);

                                        sendToPredecessor("enterConnected:" + id + ":" + nameServer.getIP() + ":" + msgArgs[3] + ":" + "bootstrap" + ":" +  this.port + ":" + oldRange[0] + ":" + oldRange[1] + ":0");
                                        predecessorIP = nameServer.getIP();
                                        predecessorPort = Integer.parseInt(msgArgs[3]);
                                        preID = id;
                                    } else {
                                        // enter:id:ipaddr:port:contactedServers
                                        sendToSuccessor("enter:" + msgArgs[1] + ":" + nameServer.getIP() + ":" + msgArgs[3] + ":0");
                                    }
                                }
                                break;
                            case "enterReturn":
                                // [enterReturn, newIP, predecessorIP, predecessorPort, successorIP, successorPort, lowerRange, upperRange, contactedServers]
                                mess = msgArgs[2] + ":" + msgArgs[3] + ":" + msgArgs[4] + ":" + msgArgs[5] + ":" + msgArgs[8];
                                server = ipToConnectionMap.remove(msgArgs[1]);
                                server.write(mess);
                                server.close();
                                break;
                            case "enterConnected":
                                // [enterConnected, id, newIP, newPort, successorIPaddr, successorPort, lowerRange, upperRange, contactedServers]
                                mess = "bootstrap::" + msgArgs[4] + ":" + msgArgs[5] + ":" + msgArgs[8] + "->0";
                                server = ipToConnectionMap.remove(msgArgs[2]);
                                successorIP = msgArgs[2];
                                successorPort = Integer.parseInt(msgArgs[3]);
                                sucID = Integer.parseInt(msgArgs[1]);
                                server.write(mess);
                                server.close();
                                break;
                            case "exit":
                                // [exit, id, successor/predecessor, newIP, newPort, lowerrange, key#values]
                                if (msgArgs[2].equalsIgnoreCase("successor")) {
                                    successorIP = msgArgs[3];
                                    successorPort = Integer.parseInt(msgArgs[4]);
                                    sucID = Integer.parseInt(msgArgs[1]);
                                } else if (msgArgs[2].equalsIgnoreCase("predecessor")) {
                                    String[] values;
                                    this.range[0] = Integer.parseInt(msgArgs[5]);
                                    if (msgArgs.length > 6) {
                                        for (int i = 6; i < msgArgs.length; i++) {
                                            values = msgArgs[i].split("#");
                                            this.table.put(Integer.parseInt(values[0]), values[1]);
                                        }
                                    }
                                    chan.write(ByteBuffer.allocate(8).putInt(1)); // send back acknowledgement
                                    predecessorIP = msgArgs[3];
                                    predecessorPort = Integer.parseInt(msgArgs[4]);
                                    preID = Integer.parseInt(msgArgs[1]);
                                }
                                nameServer.close();
                                break;
                        }
                    }
                }
                selector.selectedKeys().clear();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        boolean loop = true;
        String command;
        String[] commandArgs;
        while(loop) {
            System.out.print("> ");
            command = scanner.nextLine().trim();
            commandArgs = command.split(" ");

            try {
                switch(commandArgs[0].toLowerCase()) {
                    case "lookup":
                        if (commandArgs.length == 2) {
                            int num = Integer.parseInt(commandArgs[1]);
                            if (0 <= num && num <= 1023) {
                                lookUp(commandArgs[1]);
                            } else {
                                System.out.println("> Range must be a number from 0 to 1023");
                            }
                        } else {
                            System.out.println("> lookup is expecting 'lookup [range]'");
                        }
                        break;
                    case "insert":
                        if (commandArgs.length >= 3) {
                            int num = Integer.parseInt(commandArgs[1]);
                            if (0 <= num && num <= 1023) {
                                String[] value = Arrays.copyOfRange(commandArgs, 2, commandArgs.length);
                                insert(commandArgs[1], String.join(" ", value));
                            } else {
                                System.out.println("> Range must be a number from 0 to 1023");
                            }
                        } else {
                            System.out.println("> insert is expecting 'insert [range] [value]'");
                        }
                        break;
                    case "delete":
                        if (commandArgs.length == 2) {
                            int num = Integer.parseInt(commandArgs[1]);
                            if (0 <= num && num <= 1023) {
                                delete(commandArgs[1]);
                            } else {
                                System.out.println("> Range must be a number from 0 to 1023");
                            }
                        } else {
                            System.out.println("> delete is expecting 'delete [range]'");
                        }
                        break;
                    case "quit":
                        loop = false;
                        break;
                    default:
                        System.out.println("Command is not valid");
                        break;
                }
            } catch (NumberFormatException nfe) {
                System.out.println("Range is not a number");
            }
        }
        scanner.close();
        System.exit(0);
    }

    private void printResult(String result, String contactedServers) {
        String s = String.format("\n> %s, Contacted Servers: %s\n> ", result, contactedServers);
        System.out.print(s);
    }

    private boolean checkInRange(int key) {
        if (range[0] <= key && key <= range[1]) {
            return true;
        }
        return false;
    }

    private void sendToSuccessor(String msg) {
        try {
            Socket sock = new Socket(successorIP, successorPort);
            PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
            out.println(msg);
            out.close();
            sock.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendToPredecessor(String msg) {
        try {
            Socket sock = new Socket(predecessorIP, predecessorPort);
            PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
            out.println(msg);
            out.close();
            sock.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
}