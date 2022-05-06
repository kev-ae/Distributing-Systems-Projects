import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.channels.OverlappingFileLockException;

/**
 * Class that handles all the functionality of the server.
 */
public class serverfunction extends Thread {

    private String cur_dir;
    private Socket client;
    private DataOutputStream dout;
    private DataInputStream din;

    public serverfunction(Socket client) { /*Socket client*/
	// Initialize the socket and increase the count of thread instances
	this.client = client;
	this.cur_dir = System.getProperty("user.dir");
	try {
	    this.dout = new DataOutputStream(client.getOutputStream());
	    this.din = new DataInputStream(client.getInputStream());
	} catch (IOException ioe) {
	    ioe.printStackTrace();
	}
    }

    /**
     * void run() method: Main loop that reads client input and determines what action
     *     to take.
     */
    public void run() {
		try {
			int delimiter = 0;
			String client_input = "";
			String returnMessage = "";
			boolean success;
			boolean loop = true;

			while(loop) {
				// read from the socket
				client_input = din.readUTF();
				delimiter = client_input.indexOf('|');
				// if the command is quit or pwd
				if (delimiter == -1) {
					if (client_input.equals("quit")) {
						loop = false;
						returnMessage = "Connection closed";
					} else if (client_input.equals("pwd")) {
						returnMessage = pwd(); // go to pwd method
					} else if (client_input.equals("ls")) {
						returnMessage = ls(); // go to ls method
					}
				} else { // if the command is 2 args
					switch(client_input.substring(0, delimiter)) {
						case "get":
							success = get(client_input.substring(delimiter+1)); // go to get method
							returnMessage = (success) ? "Success" : "Failed to get the file from the server";
							break;
						case "put":				
							success = put(client_input.substring(delimiter+1)); // go to put method
							returnMessage = (success) ? "Success" : "Failed to upload the file to server";
							break;
						case "delete":
							success = delete(client_input.substring(delimiter+1)); // go to delete method
							returnMessage = (success) ? "Success" : "Failed to delete the file on the server";
							break;
						case "cd":
							success = cd(client_input.substring(delimiter+1)); // go to cd method
							returnMessage = (success) ? "Success" : "Failed to change directory";
							break;
						case "mkdir":
							success = mkdir(client_input.substring(delimiter+1)); // go to mkdir method
							returnMessage = (success) ? "Success" : "Failed to make directory";
							break;
					}
				}
				dout.writeUTF(returnMessage);
				dout.flush();
			}
			quit();
		} catch(Exception e) {
			e.printStackTrace();
			System.err.println("Something went wrong with the run method");
		}
	}

    /**
     * boolean get(String filename) method: Takes a file from the server and gives it to the client.
     */
    public boolean get(String fileName) {
		String filePath = cur_dir + File.separator + fileName;
		try {
			// initialized file to read
			FileInputStream fin = new FileInputStream(filePath);

			FileChannel chan = fin.getChannel();
			FileLock lock;
			while ((lock = chan.tryLock()) == null) {
			}
			// read from the file and write to stream
			int c;
			do {
				c = fin.read();
				dout.writeInt(c);
			} while(c != -1);
			dout.flush();
			lock.release();
			fin.close();
		} catch(IOException e) {
			// File was not found
			e.printStackTrace();
			return false;
		}
		return true;
    }

    /**
     * boolean put(String filename) method: Takes a file from the client and saves it to the server.
     */
    public boolean put(String fileName) {
		String filePath = cur_dir + File.separator + fileName;
        try {
			// initialized file to write to
			File newFile = new File(filePath);
            FileOutputStream fout = new FileOutputStream(newFile);

			FileChannel chan = fout.getChannel();
			FileLock lock;
			while ((lock = chan.tryLock()) == null) {
			}
			/*boolean counter = true;
			long lastSize;
			long curSize;
			
			while (((b = (int)din.readByte()) != -1) && counter) {
				lastSize = chan.size();
				bb.put(b);
				bb.flip();
				chan.write(bb);
				bb.clear();
				curSize = chan.size();
				if (lastSize == curSize) {
					counter = false;
				}
			}
			lock.release();
			fout.flush();
			chan.close();
			fout.close();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}*/
			// read from stream and write to file
         int c;
            do {
                c = din.readInt();
                if(c != -1) {
                    fout.write(c);
                }
            } while(c != -1);
            lock.release();
			fout.flush();
            fout.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
			return false;
		}
		return true;
    }

    /**
     * boolean delete(String file) method: Deletes a file from the server.
     */
    public boolean delete(String file) {
		String filePath = cur_dir + File.separator + file;
		try {
			// Get the file
			File fileObject = new File(filePath);
			// Delete the file
			fileObject.delete();
		} catch(NullPointerException npe) {
			npe.printStackTrace();
			return false;
		}
		return true;
    }

    /**
     * String ls() method: Lists the files in the current server directory.
     */
    public String ls() {
        File pwpath = new File(cur_dir);
		String[] fileDirNames = pwpath.list();
		String pathNames = String.join(" ", fileDirNames);
        return pathNames;
    }

    /**
     * boolean cd(String dir) method: Changes the current server directory.
     */
    public boolean cd(String dir) {
		// Split the cur directory into arrays
		String[] curDirArr = cur_dir.split(File.separator);
		String[] newDirArr = dir.split(File.separator);

		// Initialize arrays with starting values and blanks
		int total = curDirArr.length + newDirArr.length;
		int pos = 0;
		String[] finalDirArray = new String[total];
		for (int i = 0; i < curDirArr.length; i++) {
			finalDirArray[pos] = curDirArr[i];
			pos++;
		}
		for (int i = 0; i < newDirArr.length; i++) {
			finalDirArray[pos] = "";
			pos++;
		}

		// replace the corresponding .. with the correct path and create new directory
		int curPointer = curDirArr.length - 1;
		for (int newPointer = 0; newPointer < newDirArr.length; newPointer++) {
			if (newDirArr[newPointer].equals("..")) {
				finalDirArray[curPointer] = "";
				curPointer--;
				continue;
			} else if (newDirArr[newPointer].equals(".")) {
				continue;
			}
			curPointer++;
			finalDirArray[curPointer] = newDirArr[newPointer];
		}

		// turn final path the string
		String finalDirString = String.join(" ", finalDirArray);
		finalDirString = finalDirString.trim();
		finalDirString = finalDirString.replaceAll(" ", File.separator);
		finalDirString = File.separator + finalDirString;

		// test whether the final directory is valid
		File f = new File(finalDirString);
		if (f.exists() && f.isDirectory()) {
			this.cur_dir = finalDirString;
			return true;
		}
		return false;
    }

    /**
     * boolean mkdir(String dir) method: Makes a new directory in the current server directory.
     */
    public boolean mkdir(String dir) {
		String dirPath = cur_dir + File.separator + dir;
		File newDir = new File(dirPath);
		try {
			newDir.mkdirs();
		} catch (SecurityException secExpt) {
			secExpt.printStackTrace();
			return false;
		}
		return true;
    }

    /**
     * String pwd() method: Prints the current server directory to the client.
     */
    public String pwd() {
		return cur_dir;
    }

    /**
     * boolean quit() method: Quits the connection between the server and client, and closes the client process.
     */
    public boolean quit() {
		try {
			// close the client and server socket
			dout.close();
			din.close();
			client.close();
		} catch(IOException ioe) {
			ioe.printStackTrace();
			return false;
		}
		return true;
    }
}
