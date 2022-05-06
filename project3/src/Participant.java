import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.net.InetAddress;

public class Participant {

    private int id;
    private File logFile;
    private String coordinatorIP;
    private int coordinatorPort;
    private int participantPort;
    private String participantIP;
    private ServerSocket listen;
    private volatile boolean connected;
    private volatile boolean registered;
    public static void main(String args[]) throws UnknownHostException, IOException {
        if (args.length != 1) {
            System.err.println("Argument Error: Expected 'java Participant [config file]'");
            System.exit(1);
        }

        // read from config file and extract the data
        String[] data = new String[3];
        int i = 0;
        try {
            File config = new File(args[0]);
            Scanner fileReader = new Scanner(config);
            while (fileReader.hasNextLine() && i < 3) {
                data[i++] = fileReader.nextLine();
            }
            fileReader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // format the data then configuare it to the Participant program
        int id = Integer.parseInt(data[0]);
        File log = new File(data[1]);
        int space = data[2].indexOf(" ");
        String ip = data[2].substring(0, space);
        int port = Integer.parseInt(data[2].substring(space + 1));
        Participant myConsole = new Participant(id, log, ip, port);

        // initialize basic startup then run the main loop
        myConsole.run();
    }

    public Participant(int id, File logFile, String ip, int port) {
        this.id = id;
        this.logFile = logFile;
        this.coordinatorIP = ip;
        this.coordinatorPort = port;
        this.connected = false;
        this.registered = false;
    }

    /**
     * Main loop for taking in commands from the client then sending them to the coordinator
     * @throws IOException
     * @throws UnknownHostException
     */
    public void run() throws UnknownHostException, IOException {
        Scanner scanner = new Scanner(System.in);
        // Socket sock = new Socket(coordinatorIP, coordinatorPort);
        boolean loop = true;
        String command;
        String[] commandArgs;
        while(loop) {
            System.out.print("> ");
            command = scanner.nextLine();
            command = command.trim();
            commandArgs = command.trim().split(" ");
            if (commandArgs.length < 1) {
                System.out.println("Error: No command inputed");
                continue;
            }
            
            switch(commandArgs[0].toLowerCase().trim()) {
                case "register":
                    register(command);
                    break;
                case "deregister":
                    deregister(command);
                    break;
                case "disconnect":
                    disconnect(command);
                    break;
                case "reconnect":
                    reconnect(command);
                    break;
                case "msend":
                    msend(command);
                    break;
                case "quit":
                    quit();
                    break;
                default:
                    System.out.println("Error: Not a valid command");
                    break;
            }
        }
        scanner.close();
    }

    private void register(String command) {
        if (registered == false) {
            String[] temp = command.trim().split(" ");

            // check syntax
            if (temp.length != 2) {
                System.out.println("SyntaxError: Expecting 'register [portnumber]'");
                return;
            }

            // check if port is open
            try {
                this.listen = new ServerSocket(Integer.parseInt(temp[1]));
            } catch (IOException e) {
                System.out.println("RegisterError: Port has been taken");
                return;
            } catch (NumberFormatException nfe) {
                System.out.println("PortError: Did not recieve a valid Port");
                return;
            }
            this.participantPort = Integer.parseInt(temp[1]);
            this.participantIP = getIP();
            String regName = this.id + ":::register:::" + this.participantIP + ":::" + this.participantPort;
            mkListener();
            sendToCoordinator(regName);
            registered = true;
            connected = true;
        } else {
            System.out.println("RegisterError: You are already register");
        }

    }

    private void deregister(String command) {
        if (registered == true) {
            String[] temp = command.trim().split(" ");

            // check syntax
            if (temp.length != 1) {
                System.out.println("SyntaxError: Expecting 'deregister'");
                return;
            }
            String deName = this.id + ":::deregister";
            sendToCoordinator(deName);
            registered = false;
            connected = false;
        } else {
            System.out.println("DeregisterError: You are not in a group");
        }
    }

    private void disconnect(String command) throws UnknownHostException, IOException {
        if (connected == true) {
            String[] temp = command.trim().split(" ");

            // check syntax
            if (temp.length != 1) {
                System.out.println("SyntaxError: Expecting 'disconnect'");
                return;
            }
            String m = this.id + ":::disconnect";
            sendToCoordinator(m);
            connected = false;
        } else {
            System.out.println("DisconnectError: You are not connected to the group");
        }
    }

    private void reconnect(String command) throws UnknownHostException, IOException {
        if (connected == false && registered == true) {
            String[] temp = command.trim().split(" ");

            // check syntax
            if (temp.length != 2) {
                System.out.println("SyntaxError: Expecting 'reconnect [portnumber]'");
                return;
            }

            // check if port was taken
            try {
                this.listen = new ServerSocket(Integer.parseInt(temp[1]));
            } catch (IOException e) {
                System.out.println("ReconnectError: Port number has been taken");
                return;
            } catch (NumberFormatException nfe) {
                System.out.println("PortError: Did not get a valid port");
                return;
            }
            this.participantPort = Integer.parseInt(temp[1]);
            this.participantIP = getIP();
            String m = this.id + ":::reconnect:::" + this.participantIP + ":::" + this.participantPort;
            mkListener();
            sendToCoordinator(m);
            connected = true;
        } else if (registered == false) {
            System.out.println("ReconnectError: You are not currently registered for a group.");
        } else {
            System.out.println("ReconnectError: You are already connected to the group");
        }
    }

    private void msend(String msg) {
        if (connected == true && registered == true) {
            String m = this.id + ":::msend:::" + msg.substring(6);
            sendToCoordinator(m);
        } else if (connected == false && registered == true) {
            System.out.println("MSendError: You are not online to send a message");
        } else {
            System.out.println("MSendError: You are not register with the group");
        }
    }

    private void sendToCoordinator(String msg) {
        try {
            Socket sock = new Socket(coordinatorIP, coordinatorPort);
            PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
            out.println(msg);
            out.flush();
            sock.setSoTimeout(10000); // set timeout for 10 seconds, if acknowledge is not recieve, throw exception
            try {
                while(sock.getInputStream().read() >= 0) {} // acknowledgement and empty channel
            } catch (SocketTimeoutException ste) {
                System.out.println("The command failed to be sent to the coordinator");
            }
            out.close();
            sock.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mkListener() {
        Runnable listener = () -> {
            try {
                ServerSocket temp = listen; // incase user make a new serversocket but this thread is waiting to close
                Socket coor = temp.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(coor.getInputStream()));
                BufferedWriter out = new BufferedWriter(new FileWriter(logFile, true));
                String message;
                boolean loop = true;
                while (loop) {
                    message = reader.readLine();
                    if (message.equalsIgnoreCase(":::EOF:::")) {
                        loop = false;
                    } else {
                        out.write(message);
                        out.newLine();
                        out.flush();
                    }
                }
                out.close();
                reader.close();
                coor.close();
                temp.close();
                // System.out.print("Disconnected\n> "); // to see if thread is closing
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        Thread listenerThread = new Thread(listener);
        listenerThread.start();
    }

    private String getIP() {
        InetAddress localhost = null;
        try {
            localhost = InetAddress.getLocalHost();
        } catch (Exception e) {
            System.out.println("Ip Address not found, quitting.");
            System.exit(1);
        }
        return localhost.getHostAddress().trim().toString();
    }
    
    // Quit method
    private void quit() {
        if (connected == false && registered == false) {
            System.exit(0);
        } else if (connected == false && registered == true) {
            System.out.println("Still registered with server, please deregister and try again.");
        } else if (connected == true && registered == true) {
            System.out.println("Still connected to server, please disconnect and deregister, then try again");
        }
    }
}