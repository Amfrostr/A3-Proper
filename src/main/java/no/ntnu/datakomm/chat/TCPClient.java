package no.ntnu.datakomm.chat;

import javax.security.auth.login.LoginContext;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class TCPClient {
    private PrintWriter toServer;
    private BufferedReader fromServer;
    private Socket connectionSocket;


    // Hint: if you want to store a message for the last error, store it here
    private String lastError = null;

    private final List<ChatListener> listeners = new LinkedList<>();
    private Object InputStream;

    /**
     * Connect to a chat server.
     *
     * @param host host name or IP address of the chat server
     * @param port TCP port of the chat server
     * @return True on success, false otherwise
     */
    public boolean connect(String host, int port) throws IOException {
        // TODO Step 1: implement this method   DONE?
        // Hint: Remember to process all exceptions and return false on error
        // Hint: Remember to set up all the necessary input/output stream variables

        try{
            connectionSocket = new Socket(host, port);
            this.toServer = new PrintWriter(this.connectionSocket.getOutputStream(), true);
            this.fromServer = new BufferedReader(new InputStreamReader(this.connectionSocket.getInputStream()));
            return true;
        } catch (IOException e) {
            System.out.println("IOException in connect()." +
                "Host: " + host + " - port: " + port);
            return false;
        }


    }

    /**
     * Close the socket. This method must be synchronized, because several
     * threads may try to call it. For example: When "Disconnect" button is
     * pressed in the GUI thread, the connection will get closed. Meanwhile, the
     * background thread trying to read server's response will get error in the
     * input stream and may try to call this method when the socket is already
     * in the process of being closed. with "synchronized" keyword we make sure
     * that no two threads call this method in parallel.
     */
    public synchronized void disconnect() {
        // TODO Step 4: implement this method - DONE?
        // Hint: remember to check if connection is active

        synchronized (connectionSocket){
            if(isConnectionActive()){
                try {
                    connectionSocket.close();
                    flushCom();
                    onDisconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("Error on closing socket: " + e);
                }
            }
        }

    }

    /**
     * @return true if the connection is active (opened), false if not.
     */
    public boolean isConnectionActive() {
        if(connectionSocket != null) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * Cleans the toServer and fromServer
     */
    public void flushCom() {
        toServer = null;
        fromServer = null;
    }


    /**
     * Send a command to server.
     *
     * @param cmd A command. It should include the command word and optional attributes, according to the protocol.
     * @return true on success, false otherwise
     */
    private boolean sendCommand(String cmd){
        // TODO Step 2: Implement this method - DONE
        // Hint: Remember to check if connection is active
        if (cmd == null || this.connectionSocket.isClosed()) {
            return false;
        } else {
            this.toServer.println(cmd);
            return true;
        }
    }

    /**
     * Send a public message to all the recipients.
     *
     * @param message Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPublicMessage(String message) {
        // TODO Step 2: implement this method - DONE
        // Hint: Reuse sendCommand() method
                //^Yeah, but why though?
        // Hint: update lastError if you want to store the reason for the error.
        if(message == null || message.isBlank()){
            this.lastError = "Can`t send an empty message";
            return false;
        } else {
            this.sendCommand("msg " + message);
                return true;
        }

    }

    /**
     * Send a login request to the chat server.
     *
     * @param username Username to use
     */
    public void tryLogin(String username) {
        // TODO Step 3: implement this method - DONE
        // Hint: Reuse sendCommand() method
        if (username == null || username.isBlank()) {
            this.lastError = "Username is missing";
            System.out.println("Hi");
        }else {
            this.sendCommand("login " + username);
        }
    }

    /**
     * Send a request for latest user list to the server. To get the new users,
     * clear your current user list and use events in the listener.
     */
    public void refreshUserList() {
        // TODO Step 5: implement this method - DONE?
        // Hint: Use Wireshark and the provided chat client reference app to find out what commands the
        // client and server exchange for user listing.
        this.sendCommand("users");
    }

    /**
     * Send a private message to a single recipient.
     *
     * @param recipient username of the chat user who should receive the message
     * @param message   Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPrivateMessage(String recipient, String message) {
        // TODO Step 6: Implement this method
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.


        if (recipient == null || recipient.isBlank() || message == null || message.isBlank()) {
            return false;
        } else {
            sendCommand("privmsg " + recipient + " " + message);
            return true;
        }

    }


    /**
     * Send a request for the list of commands that server supports.
     */
    public void askSupportedCommands() {
        // TODO Step 8: Implement this method
        // Hint: Reuse sendCommand() method
        this.sendCommand("help");
    }


    /**
     * Wait for chat server's response
     *
     * @return one line of text (one command) received from the server
     */
    private String waitServerResponse() throws IOException {
        String serverResponse = null;

        if (!connectionSocket.isClosed()) {
            try {
                //If inputStream is null, close connection socket.
                serverResponse = this.fromServer.readLine();

            } catch (IOException e) {
                //Calling disconnect, which attempts to close the socket.
                disconnect();
                e.printStackTrace();
                lastError = e.toString();
            }

            // TODO Step 4: If you get I/O Exception or null from the stream, it means that something has gone wrong
            // with the stream and hence the socket. Probably a good idea to close the socket in that case. - DONE?



        } else {
            System.out.println("The connection is closed");
            serverResponse = null;
        }
        // TODO Step 3: Implement this method - DONE


        return serverResponse;
    }

    /**
     * Get the last error message
     *
     * @return Error message or "" if there has been no error
     */
    public String getLastError() {
        if (lastError != null) {
            return lastError;
        } else {
            return "";
        }
    }

    /**
     * Start listening for incoming commands from the server in a new CPU thread.
     */
    public void startListenThread() {
        // Call parseIncomingCommands() in the new thread.
        Thread t = new Thread(() -> {
            try {
                parseIncomingCommands();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        t.start();
    }

    /**
     * Read incoming messages one by one, generate events for the listeners. A loop that runs until
     * the connection is closed.
     */
    private void parseIncomingCommands() throws IOException {
    while (!connectionSocket.isClosed()) {
        // TODO Step 3: Implement this method - DONE??? please?
        // Hint: Reuse waitServerResponse() method
        // Hint: Have a switch-case (or other way) to check what type of response is received from the server
        // and act on it.
        // Hint: In Step 3 you need to handle only login-related responses.
        // Hint: In Step 3 reuse onLoginResult() method

        String serverResponse = waitServerResponse();
        if(serverResponse != null && !serverResponse.isBlank()){
            String commandWord = extractCmd(serverResponse);
            String message = removeCmdWord(serverResponse);

            switch (commandWord){

                case "loginok":
                    onLoginResult(true, "Logged in successfully.");
                    break;

                case "loginerr":
                    onLoginResult(false, "Login failed.");
                    break;

                case "loginerr incorrect username format":
                    onLoginResult(false, "username format incorrect.");
                    break;

                case "users":
                    onUsersList(stringArrayFromString(message, " "));
                    break;

                case "msgok 1":
                    System.out.println("message sent");
                    break;

                case "msgok":
                    System.out.println("message sent");
                    break;

                case "msg":
                    onMsgReceived(false, extractCmd(message), removeCmdWord(message));
                    break;

                case "privmsg":
                    onMsgReceived(true, extractCmd(message), removeCmdWord(message));
                    break;

                case "msgerr":
                    onMsgError(message);
                    break;

                case "cmderr":
                    onCmdError(message);
                    break;

                case "supported":
                    onSupported(stringArrayFromString(message, " "));
                    break;



                default:
                    break;
            }

        }

        // TODO Step 5: update this method, handle user-list response from the server
        // Hint: In Step 5 reuse onUserList() method

        // TODO Step 7: add support for incoming chat messages from other users (types: msg, privmsg)
        // TODO Step 7: add support for incoming message errors (type: msgerr)
        // TODO Step 7: add support for incoming command errors (type: cmderr)
        // Hint for Step 7: call corresponding onXXX() methods which will notify all the listeners

        // TODO Step 8: add support for incoming supported command list (type: supported)

    }

    }

    /**
     * Register a new listener for events (login result, incoming message, etc)
     *
     * @param listener
     */
    public void addListener(ChatListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unregister an event listener
     *
     * @param listener
     */
    public void removeListener(ChatListener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // The following methods are all event-notificators - notify all the listeners about a specific event.
    // By "event" here we mean "information received from the chat server".
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Notify listeners that login operation is complete (either with success or
     * failure)
     *
     * @param success When true, login successful. When false, it failed
     * @param errMsg  Error message if any
     */
    private void onLoginResult(boolean success, String errMsg) {
        for (ChatListener l : listeners) {
            l.onLoginResult(success, errMsg);
        }
    }

    /**
     * Notify listeners that socket was closed by the remote end (server or
     * Internet error)
     */
    private void onDisconnect() {
        // TODO Step 4: Implement this method - DONE
        // Hint: all the onXXX() methods will be similar to onLoginResult()
        for (ChatListener l : listeners) {
            l.onDisconnect();
        }

    }

    /**
     * Notify listeners that server sent us a list of currently connected users
     *
     * @param users List with usernames
     */
    private void onUsersList(String[] users) {
        for(ChatListener listener : listeners) {
            listener.onUserList(users);
        }
    }

    /**
     * Notify listeners that a message is received from the server
     *
     * @param priv   When true, this is a private message
     * @param sender Username of the sender
     * @param text   Message text
     */
    private void onMsgReceived(boolean priv, String sender, String text) {
        // TODO Step 7: Implement this method
        for(ChatListener listener : listeners) {
            TextMessage message = new TextMessage(sender, priv, text);
            listener.onMessageReceived(message);
        }
    }

    /**
     * Notify listeners that our message was not delivered
     *
     * @param errMsg Error description returned by the server
     */
    private void onMsgError(String errMsg) {
        // TODO Step 7: Implement this method
        for (ChatListener listener: listeners) {
            listener.onMessageError(errMsg);
        }
    }

    /**
     * Notify listeners that command was not understood by the server.
     *
     * @param errMsg Error message
     */
    private void onCmdError(String errMsg) {
        // TODO Step 7: Implement this method
        for(ChatListener listener: listeners) {
            listener.onCommandError(errMsg);
        }
    }

    /**
     * Notify listeners that a help response (supported commands) was received
     * from the server
     *
     * @param commands Commands supported by the server
     */
    private void onSupported(String[] commands) {
        // TODO Step 8: Implement this method
        for (ChatListener listener: listeners) {
            listener.onSupportedCommands(commands);
        }
    }


    /**
     * Extracts the first word of a string, which in this case is assumed to be the command word.
     * @param inputString
     * @return The first word of the inputString, assumed to be a command word.
     */
    private String extractCmd(String inputString) {
        return inputString.split(" ")[0];
    }

    /**
     * Builds a string where the first word is removed from the original.
     * Intended for removing the command word when asking server for list of users.
     * @param inputString
     * @return String of users, separated by whitespace.
     */
    private String removeCmdWord(String inputString){
        extractCmd(inputString);
        String[] splitString = inputString.split(" ");
        StringBuilder stringBuilder = new StringBuilder();
        ArrayList<String> arrayListSplitString = new ArrayList<>(Arrays.asList(splitString));
        for (int i = 1; i < splitString.length; i++){
            stringBuilder.append(splitString[i]).append(" ");
        }

        return stringBuilder.toString();
    }

    /**
     * Splits string with selected separator.
     * @param inputString
     * @param separator
     * @return An array of strings.
     */
    private String[] stringArrayFromString(String inputString, String separator){
        return inputString.split(separator);
    }

    private void onMessageResult(boolean success, String errMsg) {
        for (ChatListener l : listeners) {
            l.onLoginResult(success, errMsg);
        }
    }
}
