import java.io.*;
import java.nio.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class Sockspy {
    public static void main(String[] args) throws IOException {
        ServerSocket proxy_socket = new ServerSocket(8080);
        BlockingQueue q = new ArrayBlockingQueue(20);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(20,20,20,TimeUnit.SECONDS, q);
        // the following loop accepts up to 20 concurrent connections
        while (true) {
            Socket client_socket = proxy_socket.accept();
            if(executor.getActiveCount() >= 20){
                System.err.println("Active connections count reached capacity, refused connection" + client_socket.getPort());
                client_socket.close();
            }
            else {
                ConnectionHandler handler = new ConnectionHandler(client_socket);
                executor.execute(handler);
            }
        }
    }

    static class ConnectionHandler extends Thread {
        Socket client_socket;

        public ConnectionHandler(Socket client_socket) {
            this.client_socket = client_socket;
        }

        public void run() {
            Socket destination = new Socket();
            try {
                // parsing connection request from client
                client_socket.setSoTimeout(5000);
                InputStream inputStream = client_socket.getInputStream();
                boolean ver_4a = false;

                byte[] version = new byte[1];
                inputStream.read(version);
                // got socks version 5
                if (version[0] == 5) {
                    PrintWriter out = new PrintWriter(client_socket.getOutputStream());
                    System.err.println("Connection error: while parsing request: Unsupported " +
                            "Socks protocol version (got 05)");
                    System.err.println(terminate_connection("Closing"));
                    char[] cbuf = {0x00, 0x5B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
                    out.write(cbuf);
                    out.close();
                    inputStream.close();
                    client_socket.close();
                    return;
                }
                // got a wrong version which is not equal to 5
                if (version[0] != 4) {
                    System.err.println("Connection error: while parsing request: Unsupported " +
                            "Socks protocol version (got " + Integer.toHexString(version[0]) + ")");
                    System.err.println(terminate_connection("Closing"));
                    inputStream.close();
                    client_socket.close();
                    return;
                }
                byte[] command = new byte[1];
                inputStream.read(command);
                byte[] port = new byte[2];
                inputStream.read(port);
                byte[] IP = new byte[4];
                inputStream.read(IP);

                // reading remaining bytes from request
                byte[] current = new byte[1];
                inputStream.read(current);
                while (current[0] != 0x00) {
                    inputStream.read(current);
                }

                // bonus part - socks4a extension
                String host_name = "";
                if (IP[0] == 0 && IP[1] == 0 && IP[2] == 0 && IP[3] != 0) {
                    ver_4a = true;
                    byte[] curr = new byte[1];
                    inputStream.read(curr);
                    while (curr[0] != 0) {
                        host_name += (char) (curr[0]);
                        inputStream.read(curr);
                    }
                }
                // creating connection to server
                int port_number = ((port[0] & 0xff) << 8) | (port[1] & 0xff);
                //byte[] arr = new byte[4];
                InetAddress host = InetAddress.getByAddress(IP);

                try {
                    // In case we are using version 4 without the extension, the connection will be made using the given IP
                    if (!ver_4a) {
                        destination.connect(new InetSocketAddress(host.getHostAddress(), port_number), 5000);
                    }
                    // In case we are using socks4a, the connection will be done using the name of the host found
                    else {
                        destination.connect(new InetSocketAddress(host.getByName(host_name), port_number), 5000);
                    }
                    // This will catch the case in which the IP address is not valid
                } catch (SocketTimeoutException e) {
                    PrintWriter out = new PrintWriter(client_socket.getOutputStream());
                    System.err.println("Connection error: while connecting to destination: connect timed out");
                    System.err.println(terminate_connection("Closing"));
                    char[] cbuf = {0x00, 0x5B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
                    out.write(cbuf);
                    out.close();
                    inputStream.close();
                    client_socket.close();
                    return;
                }
                destination.setSoTimeout(5000);
                // this message represents a successful connection
                String message = terminate_connection("Successful") + " to " + host.getHostAddress() + ":" + port_number;
                System.err.println(message);

                // connection reply to client
                PrintWriter out = new PrintWriter(client_socket.getOutputStream(), true);
                char[] response = {0x00, 0x5A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
                out.write(response);
                out.flush();

                // sending http request from client to host

                PrintWriter out_to_host = new PrintWriter(destination.getOutputStream());
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));

                String line = in.readLine();
                String[] authorization = new String[3];

                while (line != null && line.length() != 0) {
                    // checking if the current line holds the credentials
                    String[] prefix = line.split(" ");
                    // checking for the user:password part
                    if (prefix[0].equals("Authorization:")) {
                        byte[] credDecoded = Base64.getDecoder().decode(prefix[2]);
                        authorization[0] = new String(credDecoded, StandardCharsets.UTF_8);
                    }
                    // checking for the host part
                    if (prefix[0].equals("Host:")) {
                        authorization[1] = prefix[1];
                    }
                    // checking for the path part
                    if (prefix[0].equals("GET")) {
                        authorization[2] = prefix[1];
                    }
                    out_to_host.write(line + "\r\n");
                    line = in.readLine();
                }
                // in case a password was found - print it
                if(authorization[0] != null) System.err.println("Password Found! http://" + authorization[0] +
                        "@" + authorization[1] + authorization[2]);
                out_to_host.write(line + "\r\n");
                out_to_host.flush();

                // sending http response from host to client
                BufferedReader in_from_host = new BufferedReader(new InputStreamReader(destination.getInputStream()));
                line = in_from_host.readLine();
                out.write(line + "\r\n");
                int content_length = 0;
                boolean transfer_encoding = false;
                // reading & writing headers from host to client
                while (line != null && line.length() != 0) {
                    // catching content length
                    String[] line_array = line.split(" ");
                    if (line_array[0].equals("Content-Length:")) {
                        content_length = Integer.parseInt(line_array[1]);
                    }
                    if (line_array[0].equals("Transfer-Encoding:") && line_array[1].equals("chunked")) {
                        transfer_encoding = true;
                    }
                    line = in_from_host.readLine();
                    out.write(line + "\r\n");
                    in_from_host.mark(1000);
                }
                out.flush();
                // reading & writing content from host to client
                if (content_length > 0) {
                    char[] cbuf = new char[content_length];
                    in_from_host.reset();
                    in_from_host.read(cbuf);
                    out.write(cbuf);
                    out.flush();
                }
                // in case content-length is not given as a header
                if (transfer_encoding) {
                    line = in_from_host.readLine();
                    out.write(line + "\n");
                    content_length = Integer.parseInt(line, 16);
                    int byte_counter = 0;
                    while (content_length != 0) {
                        while (byte_counter < content_length) {
                            line = in_from_host.readLine();
                            out.write(line + "\n");
                            //byte_counter += line.length();
                            byte_counter += line.getBytes().length + 1;
                        }
                        line = in_from_host.readLine();
                        out.write(line + "\n");
                        content_length = Integer.parseInt(line, 16);
                    }
                    line = in_from_host.readLine();
                    out.write(line + "\n");
                    out.flush();
                }
                System.err.println(terminate_connection("Closing") + " to " + host.getHostAddress() + ":" + port_number);
                try {
                    in.close();
                    in_from_host.close();
                    out.close();
                    out_to_host.close();
                    client_socket.close();
                    destination.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            //  This catch statement will catch the case in which a socket timed out
            catch (SocketTimeoutException e) {
                System.err.println("Connecting error: connection timed out");
                System.err.println(terminate_connection("Closing"));
                try {
                    client_socket.close();
                    destination.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } catch (IOException e) {
                System.err.println("cause: " + e.getCause());
                e.printStackTrace();
            }
        }

        // private function which returns a string that represents the closing connection message
        private String terminate_connection(String prefix) {
            byte[] bytes_of_client_ip = client_socket.getLocalAddress().getAddress();
            String result = "";
            int count = 4;
            for (byte b : bytes_of_client_ip) {
                result += (b & 0xff);
                if (--count > 0) {
                    result += ".";
                }
            }
            return prefix + " connection from " + result + ":" + client_socket.getPort();
        }
    }
}