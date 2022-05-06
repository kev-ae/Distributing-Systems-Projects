import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class myftp {

    private Socket normalSock;
    private Socket interruptSock;
    private DataInputStream din;
    private DataOutputStream dout;
    private DataOutputStream interruptDout;
    private DataInputStream interruptDin;
    public boolean prevInterrupt = false;

    public static void main(String[] args) {
        if(args.length != 3) {
            System.out.println("Error: You need to provide the program with the machine name that hold the server and the port numbers");
            System.exit(1);
        }

        String host = args[0];
        int normalPort = 5000;
        int interruptPort = 6000;
        try {
            normalPort = Integer.parseInt(args[1]);
            interruptPort = Integer.parseInt(args[2]);
        } catch(NumberFormatException nfe) {
            System.out.println("Error: Program did not receive a valid port number or terminal port");
            System.exit(2);
        }
        myftp f = new myftp(host, normalPort, interruptPort);
        Scanner user = new Scanner(System.in);
        String command = "";
        String[] commandArgs;
        String serverResponse = "";
        
        // loop
        boolean loop = true;
        while (loop) {
            System.out.print("mytftp> ");

            // get rid of extra whitespace and get the number of args
            command = user.nextLine();
            command = command.trim();
            commandArgs = command.split(" ");
            if (commandArgs.length == 1) {
                if (command.equals("quit")) { // quit the client
                    f.interruptCommand(command);
                    f.terminateResponse();
                    f.sendCommand(command);
                    loop = false;
                } else if (command.equals("pwd") || command.equals("ls")) { // either pwd or ls then print results
                    f.sendCommand(command);
                }
                System.out.println("myftpserver> " + f.statusResponse());
            } else if (commandArgs.length == 2) {
                // send args to server
                if (commandArgs[0].equals("get")) { // go to get method
                    f.sendCommand(command);
                    f.responseOfGet(commandArgs[1]);
                    serverResponse = f.statusResponse();
                } else if (commandArgs[0].equals("put")) { // go to put method
                    f.sendCommand(command);
                    f.put(commandArgs[1]);
                    serverResponse = f.statusResponse();
                } else if (commandArgs[0].equals("terminate")) { // interrupt the previous get or put method
                    f.prevInterrupt = true;
                    f.interruptCommand(command);
                    serverResponse = f.terminateResponse();
                } else { // rest of commands
                    f.sendCommand(command);
                    serverResponse = f.statusResponse();
                }
                System.out.println("myftpserver> " + serverResponse);
            } else if (commandArgs.length == 3 && commandArgs[2].equals("&")) { // bg get and put processes
                final String bgcommand = command;
                final String fileName = commandArgs[1];
                if (commandArgs[0].equals("get")) { // run the get method in the bg
                    f.sendCommand(bgcommand);
                    System.out.println("myftpserver> " + f.statusResponse());
                    Runnable getbg = () -> {
                        f.responseOfGet("temp_" + fileName);
                        if (f.prevInterrupt) {
                            File temp = new File("temp_" + fileName);
                            temp.delete();
                            f.prevInterrupt = false;
                        } else {
                            new File(fileName).delete();
                            new File("temp_" + fileName).renameTo(new File(fileName));
                            System.out.print("\nmyftpserver> " + f.statusResponse() + "\nmyftp> ");
                        }
                    };
                    Thread getThread = new Thread(getbg);
                    getThread.start();
                } else if (commandArgs[0].equals("put")) { // change put method to change prevInterrupt back to false
                    f.sendCommand(bgcommand);
                    System.out.println("myftpserver> " + f.statusResponse());
                    Runnable putbg = () -> {
                        f.put(fileName);
                        if (f.prevInterrupt) {
                            f.prevInterrupt = false;
                        } else {
                            System.out.print("\nmyftpserver> " + f.statusResponse() + "\nmyftp> ");
                        }
                    };
                    Thread putThread = new Thread(putbg);
                    putThread.start();
                }
            } else {
                System.out.println("That is not a valid command");
                continue;
            }
        }
        
        // Close program cleanly
        user.close();
        f.exit();
    }

    public myftp(String host, int normalPort, int interruptPort) {
        try {
            // Create a socket and bind it to the given port
            this.normalSock = new Socket(host, normalPort);
            this.interruptSock = new Socket(host, interruptPort);
            this.din = new DataInputStream(normalSock.getInputStream());
            this.dout = new DataOutputStream(normalSock.getOutputStream());
            this.interruptDout = new DataOutputStream(interruptSock.getOutputStream());
            this.interruptDin = new DataInputStream(interruptSock.getInputStream());
        } catch (Exception e) {
            System.err.println("There was an error with creating the socket");
            e.printStackTrace();
        }
    }

    public void sendCommand(String command) {
        try {
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
            FileInputStream fin = new FileInputStream(fileName);

            // read from file and write to stream
			int c = 0;
            int checker = 0;
			do {
                if (checker == 1000 && prevInterrupt == true) {
                    c = -1;
                } else {
				    c = fin.read();
                }
				dout.writeInt(c);
                checker = (checker + 1) % 1001;
			} while(c != -1);
            dout.flush();
			fin.close();
		} catch(IOException e) {
			// File was not found
			e.printStackTrace();
			System.err.println("Was unable to get the file: put method");
		}
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

    public String terminateResponse() {
        String response = "";
        try {
            response = interruptDin.readUTF();
        } catch (IOException ioe) {
            System.out.println("There was an error with terminating the process");
        }
        return response;
    }

    public void interruptCommand(String command) {
        try {
            interruptDout.writeUTF(command);
        } catch (IOException ioe) {
            System.out.println("There was an error with sending the terminate command to the server");
        }
    }

    private void exit() {
        try {
            // close socket and streams
            din.close();
            dout.close();
            interruptDout.close();
            interruptDin.close();
            normalSock.close();
            interruptSock.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.err.println("IOException in exit: there was an issue with closing the socket, inputstream, or outputstream");
        }
    }
}
