import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Coordinator {
    private long timeout; // in milli
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private SelectionKey serverKey;
    private ConcurrentLinkedQueue<Command> taskPipeLine = new ConcurrentLinkedQueue<>();
    private ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Command>> clientsQueue = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Boolean[]> clientsStatus = new ConcurrentHashMap<>(); // online status and reconnecting
    private ConcurrentHashMap<Integer, Socket> clientsSocket = new ConcurrentHashMap<>();

    public static void main(String args[]) throws IOException {
        if (args.length != 1) {
            System.err.println("Argument Error: Expected 'java Coordinator [config file]'");
            System.exit(1);
        }

        // read config file
        String[] data = new String[2];
        int i = 0;
        try {
            File config = new File(args[0]);
            Scanner fileReader = new Scanner(config);
            while (fileReader.hasNextLine() && i < 2) {
                data[i++] = fileReader.nextLine();
            }
            fileReader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // parse config and start the coordinator
        int port = Integer.parseInt(data[0]);
        long timeout = Integer.parseInt(data[1]) * 1000;
        InetAddress lh = InetAddress.getLocalHost();
        InetSocketAddress listen = new InetSocketAddress(lh, port);
        Coordinator server = new Coordinator(timeout, listen);
        server.start();
    }

    public Coordinator(long timeout, InetSocketAddress listenPort) throws IOException {
        this.timeout = timeout;
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false); // for asyncronous
        this.serverKey = serverChannel.register(this.selector = Selector.open(), SelectionKey.OP_ACCEPT);
        this.serverChannel.bind(listenPort);
    }

    public void start() {
        // listen on a port for both connection and commands
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                selector.selectNow();

                for(SelectionKey key : selector.selectedKeys()) {
                    if (!key.isValid()) {
                        continue;
                    }

                    // if accept even occur, accept new connection and add to key set
                    if (key == serverKey) {
                        SocketChannel chan = serverChannel.accept();
                        if (chan == null) continue;
                        chan = (SocketChannel) chan.configureBlocking(false);
                        ByteBuffer buf = ByteBuffer.allocate(1024);

                        // get the message
                        String command = "";
                        try {
                            while (chan.read(buf) > 0) {
                                buf.flip();
                                command += StandardCharsets.UTF_8.decode(buf).toString();
                                buf.clear();
                            }         
                        } catch (Exception e) {
                            if (chan != null) {
                                chan.close();
                            }
                            e.printStackTrace();
                        }
                        ByteBuffer ack = ByteBuffer.allocate(8).putInt(1);
                        chan.write(ack); // send back acknowledgement
                        chan.close();

                        String[] properties = command.trim().split(":::");
                        Command task;
                        if (properties.length == 4) {
                            // id, command, ip, port (register and reconnect)
                            task = new Command(Integer.parseInt(properties[0]), properties[1], properties[2], Integer.parseInt(properties[3]));
                        } else if (properties.length == 3) {
                            // id, command, msg (msend)
                            task = new Command(Integer.parseInt(properties[0]), properties[1], properties[2]);
                        } else {
                            // id, command (deregister, disconnect)
                            task = new Command(Integer.parseInt(properties[0]), properties[1]);
                        }
                        // put in main queue
                        taskPipeLine.add(task);
                    }
                }
                selector.selectedKeys().clear(); // clear the selection keys so it won't repeat commands
            } catch(Exception e) {
                e.printStackTrace();
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

        // thread pool that will periodically pull from the task queue and do a task
        Executors.newScheduledThreadPool(10).scheduleAtFixedRate(() -> {
            if (!taskPipeLine.isEmpty()) {
                Command task = taskPipeLine.poll();
                String command = task.getCommand().toLowerCase();
                switch (command) {
                    case "register":
                        register(task);
                        break;
                    case "deregister":
                        deregister(task);
                        break;
                    case "disconnect":
                        disconnect(task);
                        break;
                    case "reconnect":
                        reconnect(task);
                        break;
                    case "msend":
                        msend(task);
                        break;
                }
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void register(Command task) {
        try {
            Socket asock = new Socket(task.getIP(), task.getPort());
            clientsSocket.put(task.getID(), asock);
            Boolean[] defBools = {true , false};
            clientsStatus.put(task.getID(), defBools);
            ConcurrentLinkedQueue<Command> taskQueue = new ConcurrentLinkedQueue<>(); 
            clientsQueue.put(task.getID(), taskQueue); 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deregister(Command task) {
        int id = task.getID();
        if (clientsStatus.get(id)[0] || clientsStatus.get(id)[1]) {
            disconnect(task);
            clientsStatus.remove(id);
            ConcurrentLinkedQueue<Command> goneQueue = clientsQueue.remove(id);
            goneQueue.clear();
        } else {
            clientsStatus.remove(id);
            ConcurrentLinkedQueue<Command> goneQueue = clientsQueue.remove(id);
            goneQueue.clear();
            Socket sock = clientsSocket.remove(id);
            try {
                if (sock != null) { // if deregister offline
                    sock.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void disconnect(Command task) {
        int id = task.getID();
        synchronized (clientsStatus.get(id)) {
            clientsStatus.get(id)[0] = false; // set online status to false
            clientsStatus.get(id)[1] = false; // set reconnecting to false (in situation where it is reconnecting and client call disconnect)
            sendToParticipant(id, ":::EOF:::");
        }
        Socket sock = clientsSocket.remove(id);
        try {
            if (sock != null) {
                sock.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void reconnect(Command task) {
        int port = task.getPort();
        String ip = task.getIP();
        int id = task.getID();
        clientsStatus.get(id)[1] = true; // reconnecting status
        try {
            clientsSocket.put(id, new Socket(ip, port));
            ConcurrentLinkedQueue<Command> queue = clientsQueue.get(id);
            if (queue != null) {
                boolean loop = true;
                while (loop && !queue.isEmpty()) {
                    synchronized (clientsStatus.get(id)) {
                        Boolean[] temp = clientsStatus.get(id);
                        if ((temp == null) || (!temp[1])) {
                            loop = false;
                        } else {
                            Command msg = queue.poll();
                            // check if message has pass timeout
                            if ((msg != null) && (!msg.isExpired(this.timeout))) {
                                sendToParticipant(id, msg.getMsg());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (clientsStatus.get(id)[1]) {
            clientsStatus.get(id)[1] = false; // reset reconnecting status
            clientsStatus.get(id)[0] = true; // change online status
        }
    }

    private void msend(Command task) { // get task
        // For each participant
        clientsStatus.forEachKey(Long.MAX_VALUE, key -> { // begin function, adding to queues
            // If online and not reconnecting
            if (clientsStatus.get(key)[0] == true && clientsStatus.get(key)[1] == false) {
                task.putTime(System.currentTimeMillis()); // update time
                // Add to queue
                ConcurrentLinkedQueue<Command> queue = clientsQueue.get(key);
                if (queue != null) {
                    queue.add(task);
                    // Empty the queue
                    boolean loop = true;
                    while (loop && !queue.isEmpty()) {
                        synchronized (clientsStatus.get(key)) { // synchronize
                            Boolean[] temp = clientsStatus.get(key);
                            if ((temp == null) || (!temp[0])) {
                                loop = false;
                            } else {
                                Command msg = queue.poll();
                                // check if message has pass timeout
                                if ((msg != null) && (!msg.isExpired(this.timeout))) {
                                    sendToParticipant(key, msg.getMsg());
                                }
                            }
                        }
                    }
                } 
            } else { // if reconnecting or offline
                // Add to queue
                task.putTime(System.currentTimeMillis());
                ConcurrentLinkedQueue<Command> temp = clientsQueue.get(key);
                if (temp != null) {
                    temp.add(task);
                }
            }
        }); // end function
    }

    private void sendToParticipant(int id, String msg) {
        try {
            PrintWriter pw = new PrintWriter(clientsSocket.get(id).getOutputStream(), true);
            pw.println(msg);
            pw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}