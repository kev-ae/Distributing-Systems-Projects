import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class myftp {

    private Socket sock;
    private DataInputStream din;
    private DataOutputStream dout;    

    public static void main(String[] args) {
        //if(args.length != 2) {
      //      System.out.println("Error: You need to provide the program with the machine name that hold the server and the port number");
       //     System.exit(1);
      //  }

        //String host = args[0];
        //int port = 5000;
        //try {
        //    port = Integer.parseInt(args[1]);
        //} catch(NumberFormatException nfe) {
		/*
		 * System.out.println("Error: Program did not receive a valid port number");
		 * System.exit(2); }
		 */
        myftp f = new myftp("localhost", 5000);
        Scanner user = new Scanner(System.in);
        String command = "";
        String[] numberOfArgs;
        int divider = 0;
        
        // loop
        boolean loop = true;
        while (loop) {
            System.out.print("mytftp> ");

            // get rid of extra whitespace and get the number of args
            command = user.nextLine();
            command = command.trim();
            numberOfArgs = command.split(" ");
            if (numberOfArgs.length == 1) {
                if (command.equals("quit")) {
                    f.sendCommand(command);
                    loop = false;
                } else if (command.equals("pwd") || command.equals("ls")) {
                    f.sendCommand(command);
                }
            } else if (numberOfArgs.length == 2) {
                // parse the command
                command = command.replaceAll(" ", "|");
                divider = command.indexOf('|');
                // send args to server
                if (command.substring(0,divider).equals("get")) {
                    f.sendCommand(command);
                    f.responseOfGet(command.substring(divider+1));
                } else if (command.substring(0,divider).equals("put")) {
                    f.sendCommand(command);
                    f.put(command.substring(divider+1));
                } else {
                    f.sendCommand(command);
                }
            } else {
                System.out.println("That is not a valid command");
                continue;
            }

            System.out.println("myftpserver> " + f.statusResponse());
        }
        
        // Close program cleanly
        user.close();
        f.exit();
    }

    public myftp(String host, int port) {
        try {
            // Create a socket and bind it to the given port
            this.sock = new Socket(host, port); // replace localhost with host that has our server
            this.din = new DataInputStream(sock.getInputStream());
            this.dout = new DataOutputStream(sock.getOutputStream());
        } catch (Exception e) {
            System.err.println("There was an error with creating the socket");
            e.printStackTrace();
        }
    }

    public void sendCommand(String command) {
        try {
        	dout.flush();
            dout.writeUTF(command);
        } catch (IOException ioe) {
            System.out.println("There was an error with sending the command to the server");
        }
    }

    private void responseOfGet(String fileName) {
        try {
            // initialize file to write to
            File newFile = new File(fileName);
            FileOutputStream fout = new FileOutputStream(newFile);

            // read from stream and write to file
            int c;
            do {
                c = din.readInt();
                if(c != -1) {
                    fout.write(c);
                }
            } while(c != -1);
            fout.flush();
            fout.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.err.println("IOException in responseOfGet: there was an error with getting the input stream");
        }
    }

    private void put(String fileName) {
        try {
            Path path = Paths.get(fileName);
            FileChannel chan = FileChannel.open(path);
            ByteBuffer bb = ByteBuffer.allocate(4000);
            int bRead = 0;
            while ((bRead = chan.read(bb)) != -1) {
                bb.flip();
                while (bb.hasRemaining()) {
                    dout.writeByte(bb.get());
                }
            } 
            bb.clear();
            dout.flush();
            chan.close();            
            } catch (Exception e) {
                e.printStackTrace();
            }
            // read from file and write to stream
			/**int c;
			do {
				c = fin.read();
				dout.writeInt(c);
			} while(c != -1);
            dout.flush();
			fin.close();
		} catch(IOException e) {
			// File was not found
			e.printStackTrace();
			System.err.println("Was unable to get the file: put method");
        */
    }

    public String statusResponse() {
        String response = "";
        try {
            response = din.readUTF();
        } catch (IOException ioe) {
            System.out.println("There was an error with getting the response from the server");
        }
        return response;
    }

    private void exit() {
        try {
            // close socket and streams
            sock.close();
            din.close();
            dout.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.err.println("IOException in exit: there was an issue with closing the socket, inputstream, or outputstream");
        }
    }
}
