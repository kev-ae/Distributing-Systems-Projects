import java.net.ServerSocket;
import java.net.Socket;

/**
 * Class that handle high level overview of server
 */
public class myftpserver {
	// Socket that the server is bind to
	private ServerSocket normalSock;
	private ServerSocket interruptSock;
	private int normalPort;
	private int interruptPort;

	/**
	 * Constructor that takes in a port and binds the {@code ServerSocket} to the port.
	 * 
	 * @param port
	 */
	public myftpserver(int normalPort, int interruptPort) {
		try {
			this.normalPort = normalPort;
			this.interruptPort = interruptPort;
			this.normalSock = new ServerSocket(normalPort);
			this.interruptSock = new ServerSocket(interruptPort);
		} catch(Exception e) {
			e.printStackTrace();
			System.err.println(e);
		}
	}

	/**
	 * Server loop, might add quit function to cleanly end server. Wait and listen for a connection, then start an instance of the server.
	 * 
	 */
	private void start() {
		while(true) {
			try {
				this.startInstance(normalSock.accept(), interruptSock.accept());
			} catch(Exception e) {
				e.printStackTrace();
				System.err.println(e);
			}
		}
	}

	/**
	 * Create a new thread with the client socket
	 * @param client
	 */
	private void startInstance(Socket client, Socket clientInterrupt) {
		serverfunction instance = new serverfunction(client, clientInterrupt);
		instance.start();
	}

	public static void main(String[] args) {
		if(args.length != 2) {
			System.out.println("You need to provide the port numbers this server will listen to");
			System.exit(1);
		}
		int normalPort = 5000;
		int interruptPort = 6000;
		try {
			normalPort = Integer.parseInt(args[0]);
			interruptPort = Integer.parseInt(args[1]);
		} catch(NumberFormatException nfe) {
			System.out.println("Failed: Not a value port number");
			System.exit(2);
		}
		myftpserver server = new myftpserver(normalPort, interruptPort);
		server.start();
	}
}
