import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;

/**
 * Class that handles all the functionality of the server.
 */
public class serverfunction extends Thread {

	private String cur_dir;
	private Socket client;
	private Socket clientInterrupt;
	private DataOutputStream dout;
	private DataInputStream din;
	private DataInputStream interruptDin;
	private DataOutputStream interruptDout;
	private boolean interrupt = false;
	private int pidCounter = 1;
	private static HashMap<String, fileproperties> lockedFiles = new HashMap<>();
	private HashMap<Integer, Boolean> pidList = new HashMap<>();

    public serverfunction(Socket client, Socket clientInterrupt) { /*Socket client*/
		// Initialize the socket and increase the count of thread instances
		this.client = client;
		this.cur_dir = System.getProperty("user.dir");
		this.clientInterrupt = clientInterrupt;
		this.pidList.put(0, false); // default pid if no & is given
		try {
			this.dout = new DataOutputStream(client.getOutputStream());
			this.din = new DataInputStream(client.getInputStream());
			this.interruptDin = new DataInputStream(clientInterrupt.getInputStream());
			this.interruptDout = new DataOutputStream(clientInterrupt.getOutputStream());
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
			Runnable r = () -> { 
				String[] ar;
				String var = "";
				boolean loop = true;
				try{
					while(loop) {
						var = interruptDin.readUTF();
						ar = var.split(" "); 
						if(ar[0].equals("terminate")) {
							if(pidList.containsKey(Integer.parseInt(ar[1]))) {
								pidList.put(Integer.parseInt(ar[1]) , true);
								interrupt = true;
							} else {
								interruptDout.writeUTF("PID does not exist");
								interruptDout.flush();
							}
						} else if(ar[0].equals("quit")) {
							loop = false;
						}
					}
					interruptDout.writeUTF("");
					interruptDout.flush();
				} catch(IOException ioe){
					ioe.printStackTrace();
				} 
			};
			Thread t = new Thread(r);
			t.start(); 
			String[] args;
			String client_input = "";
			String returnMessage = "";
			boolean success;
			
			boolean loop = true;

			while(loop) {
				// read from the socket
				client_input = din.readUTF();
				args = client_input.split(" ");
				// if the command is quit or pwd
				if (args.length == 1) {
					if (client_input.equals("quit")) {
						loop = false;
						returnMessage = "Connection closed";
					} else if (client_input.equals("pwd")) {
						returnMessage = pwd(); // go to pwd method
					} else if (client_input.equals("ls")) {
						returnMessage = ls(); // go to ls method
					}
				} else { // rest of commands
					int pid = 0;
					switch(args[0]) {
						case "get":
							if (args.length == 3 && args[2].equals("&")) {
								pid = pidCounter;
								dout.writeUTF("Process PID is " + pidCounter);
								dout.flush();
								pidList.put(pidCounter, false);
								pidCounter++;
							}
							success = get(args[1], pid); // go to get method
							returnMessage = (success) ? "Success" : "Failed to get the file from the server";
							break;
						case "put":
							if (args.length == 3 && args[2].equals("&")) {
								pid = pidCounter;
								dout.writeUTF("Process PID is " + pidCounter);
								dout.flush();
								pidList.put(pidCounter, false);
								pidCounter++;
							}
							success = put(args[1], pid); // go to put method
							returnMessage = (success) ? "Success" : "Failed to upload the file to server";
							break;
						case "delete":
							success = delete(args[1]); // go to delete method
							returnMessage = (success) ? "Success" : "Failed to delete the file on the server";
							break;
						case "cd":
							success = cd(args[1]); // go to cd method
							returnMessage = (success) ? "Success" : "Failed to change directory";
							break;
						case "mkdir":
							success = mkdir(args[1]); // go to mkdir method
							returnMessage = (success) ? "Success" : "Failed to make directory";
							break;
					}
				}
				if (interrupt) {
					interruptDout.writeUTF("Process has been terminated");
					interruptDout.flush();
					interrupt = false;
				} else {
					dout.writeUTF(returnMessage);
					dout.flush();
				}
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
    public boolean get(String fileName, int pid) {
		String filePath = cur_dir + File.separator + fileName;
		if (lockedFiles.get(filePath) == null) {
			lockedFiles.put(filePath, new fileproperties(new File(filePath)));
		}
		
		// check if file exist
		if (!lockedFiles.get(filePath).getFile().exists()) {
			try {
				dout.writeInt(-1);
				dout.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		}

		if (!lockedFiles.get(filePath).getWait()) {
			try {
				lockedFiles.get(filePath).setWaiting(true);
				synchronized(lockedFiles.get(filePath)) {
					lockedFiles.get(filePath).setWaiting(true); // on the off chance multiple read requests somehow got in this if branch
					lockedFiles.get(filePath).setReadCur(true);
					// initialized file to read
					FileInputStream fin = new FileInputStream(filePath);

					boolean result = getHelper(fin, pid);
					lockedFiles.get(filePath).setWaiting(false);
					lockedFiles.get(filePath).setReadCur(false);
					if (!result) {
						return false;
					}
				}
			} catch(Exception e) {
				// File was not found or deleted
				return false;
			}
		} else {
			try {
				fileproperties obj = lockedFiles.get(filePath);
				while (!obj.getCur()) {
					// on the off chance a request is stuck in this loop and there is no read in line waiting
					if (!obj.getWait()) break;

					// terminate command
					if (pid != 0 && pidList.get(pid) == true) {
						dout.writeInt(-1);
						dout.flush();
						return false;
					}
				}
				obj.addCount();
				FileInputStream fin = new FileInputStream(filePath);
				boolean result = getHelper(fin, pid);
				obj.removeCount();
				if (!result) {
					return false;
				}
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;
    }

	private boolean getHelper(FileInputStream fin, int pid) throws IOException {
		// read from the file and write to stream
		int c = 0;
		int checker = 0;
		try {
			do {
				if (checker == 1000 && pidList.get(pid) == true) {
					c = -1;
					pidList.remove(pid);
				} else {
					c = fin.read();
				}
				dout.writeInt(c);
				checker = (checker + 1) % 1001;
			} while(c != -1);

			if (pid != 0) {
				pidList.remove(pid);
			}

			dout.flush();
			fin.close();
		} catch (Exception e) {
			dout.writeInt(-1);
			dout.flush();
			return false;
		}
		return true;
	}

    /**
     * boolean put(String filename) method: Takes a file from the client and saves it to the server.
     */
    public boolean put(String fileName, int pid) {
		String filePath = cur_dir + File.separator + fileName;
		if (lockedFiles.get(filePath) == null) {
			lockedFiles.put(filePath, new fileproperties(new File(filePath)));
		}
		try {
			synchronized(lockedFiles.get(filePath)) {
				while (lockedFiles.get(filePath).getCount() > 0) {
					// terminate command
					if (pid != 0 && pidList.get(pid) == true) {
						emptyInput(); // empty the input stream so no random value from prev interrupt
						return false;
					}
				}

				// create a temp file to restore value in case terminate command is called
				File newFile;
				String temp = cur_dir + File.separator + "temp_" + fileName;
				if (pid == 0) {
					newFile = lockedFiles.get(filePath).getFile();
				} else {
					newFile = new File(temp);
				}
				FileOutputStream fout = new FileOutputStream(newFile);

				// read from stream and write to file
				int c = 0;
				do {
					c = din.readInt();
					if(c != -1) {
						fout.write(c);
					}
				} while(c != -1);
				fout.flush();
				fout.close();

				// delete temp if teminate, or replace old file with new one
				if (pid != 0) {
					if (pidList.get(pid)) {
						delete("temp_" + fileName);
					} else {
						File f = lockedFiles.get(filePath).remove();
						f.delete();
						new File(temp).renameTo(new File(filePath));
						lockedFiles.get(filePath).addFile(new File(filePath));;
					}
					pidList.remove(pid);
				}
			}
		} catch (Exception e) {
			// file was delete or was not found
			return false;
		}
		return true;
    }

	/**
	 * Empty the input stream so that next call does not get incorrect values from a terminate call
	 */
	private void emptyInput() {
		int c;
		try {
			do {
				c = din.readInt();
			} while (c != -1);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    /**
     * boolean delete(String file) method: Deletes a file from the server.
     */
    public boolean delete(String file) {
		String filePath = cur_dir + File.separator + file;
		int timeout = 0;
		try {
			if (lockedFiles.get(filePath) != null) {
				synchronized(lockedFiles.get(filePath)) {
					while (lockedFiles.get(filePath).getCount() > 0) {
						// terminate command or 10 min timeout
						if (timeout == 600) {
							return false;
						}
						Thread.sleep(1000);
						timeout++;
					}

					// Delete the file
					lockedFiles.remove(filePath).remove();
					File temp = new File(filePath);
					temp.delete();
				}
			} else {
				// Get the file
				File fileObject = new File(filePath);
				// Delete the file
				fileObject.delete();
			}
		} catch (Exception e) {
			e.printStackTrace();
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
			interruptDin.close();
			interruptDout.close();
			clientInterrupt.close();
		} catch(IOException ioe) {
			ioe.printStackTrace();
			return false;
		}
		return true;
    }
}
