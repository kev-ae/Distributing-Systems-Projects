import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

public class Connection {
    private SocketChannel chan;
    private ByteBuffer buf;
    private String ipAddr;

    public Connection(SocketChannel chan) throws IOException{
        this.chan = (SocketChannel) chan.configureBlocking(false);
        buf = ByteBuffer.allocate(1024);
        ipAddr = "";
    }

    public void close() {
        if (chan == null) {
            return;
        }

        try {
            chan.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String read() {
        String command = "";
        try {
            while (chan.read(buf) > 0) {
                buf.flip();
                command += StandardCharsets.UTF_8.decode(buf).toString();
                buf.clear();
            }
        } catch (Exception e) {
            close();
            e.printStackTrace();
        }
        return command;
    }

    public void write(String msg) {
        msg = msg + "\n";
        try {
            CharsetEncoder enc = Charset.forName("US-ASCII").newEncoder();
            chan.write(enc.encode(CharBuffer.wrap(msg)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getIP() {
        return ipAddr;
    }

    public void setIP(String ip) {
        this.ipAddr = ip;
    }
}
