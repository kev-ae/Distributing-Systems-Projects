import java.net.ServerSocket;
import java.net.Socket;

/**
 * Class that handle high level overview of server
 */
public class myftpserver {
	// Socket that the server is bind to
	private ServerSocket server;
	private int port;

	/**
	 * Constructor that takes in a port and binds the {@code ServerSocket} to the port.
	 * 
	 * @param port
	 */
	public myftpserver(int port) {
		try {
			this.port = port;
			server = new ServerSocket(port);
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
				this.startInstance(server.accept());
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
	private void startInstance(Socket client) {
		serverfunction instance = new serverfunction(client);
		instance.start();
	}

	public static void main(String[] args) {
		if(args.length != 1) {
			System.out.println("You need to provide the port number this server will listen to");
			System.exit(1);
		}
		int port = 5000;
		try {
			port = Integer.parseInt(args[0]);
		} catch(NumberFormatException nfe) {
			System.out.println("Failed: Not a value port number");
			System.exit(2);
		}
		myftpserver server = new myftpserver(port);
		server.start();
	}
}
