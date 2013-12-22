package com.deepdownstudios.smsolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class SMSocket {
	public Socket socket;
	public BufferedReader socketReader;
	public PrintWriter socketWriter;
	public PrintWriter consoleWriter;
	public Thread thread;
	protected SMSolver smsolver;

	private static final String BEGIN_TAG = "BEGIN";
	private static final String END_TAG = "END";
	private static final String EOL = "\n";
	private static final String ERROR_TAG = "ERROR";
	
	private static String DEFAULT_BRIDGE_IP = "localhost";
	private static int DEFAULT_BRIDGE_PORT = 9296;

	protected SMSocket(final SMSolver smsolver, final Socket socket, final BufferedReader socketReader, 
			final PrintWriter socketWriter, final PrintWriter consoleWriter) {
		assert smsolver != null && socket != null && socketReader != null && socketWriter != null && consoleWriter != null;
		this.smsolver = smsolver;
		this.socket = socket;
		this.socketReader = socketReader;
		this.socketWriter = socketWriter;
		this.consoleWriter = consoleWriter;
		
		// Run a thread that reconstructs messages from the SMBridge.  Exceptions are logged but do not
		// interrupt running unless the exception indicates that the connection to the SMBridge is broken.
		thread = new Thread( new Runnable() {
			public void run() {
				while (true) {
					try {
						String line = socketReader.readLine();
						if (!line.equals(BEGIN_TAG))
							System.err.println("WARNING: Malformed message.  Start tag missing.  Received:\n" + line);
						else
							line = ""; // only keep data if it wasnt start tag
	
						String message = "";
						while (!line.equals(END_TAG)) {
							message = message + line;
							line = socketReader.readLine();
						}
						
						CommandResult result;
						try {
							result = smsolver.execute(message);
						} catch (CommandException e) {
							sendToWebSocket(e);
							continue;
						}
						sendToWebSocket(result);
						//consoleWriter.println("Response sent");		// execute takes care of notifying user
					} catch(SocketException e) {
						if("Socket closed".equals(e.getMessage()))	{
							consoleWriter.println("Socket closed.");
							break;		// main thread terminated us
						}
					} catch (IOException e) {
						System.err.println("ERROR: Exception while reading from bridge: " + e);
						e.printStackTrace();
					}
				}
			}
		});
		thread.start();
	}
	
	protected void sendToWebSocket(CommandException ex) {
		if(socketWriter == null)
			return;
		socketWriter.print(BEGIN_TAG + EOL + ERROR_TAG + EOL + ex.getMessage() + EOL + END_TAG + EOL);
		socketWriter.flush();
	}

	public SMSocket(final SMSolver smsolver, final PrintWriter consoleWriter) {
		assert smsolver != null && consoleWriter != null;
		this.smsolver = smsolver;
		this.socket = null;
		this.socketReader = null;
		this.socketWriter = null;
		this.consoleWriter = consoleWriter;
	}

	protected synchronized void sendToWebSocket(CommandResult result) {
		if(socketWriter == null)
			return;
		socketWriter.print(BEGIN_TAG + EOL + result + EOL + END_TAG + EOL);
		socketWriter.flush();
	}

	public void close() throws IOException {
		if(thread != null)	{
			thread.interrupt();
		}

		// The interrupt is supposed to cause socketReader.readLine to throw an Exception but it doesn't.  
		// socketReader.close() then hangs.  Note that the docs conflict on what to do here.  The Socket Javadoc says that
		// closing the reader or writer closes the whole socket (implying socket.close() any one close() alone is fine).
		// The JavaSE book, OTOH, suggests that you *need* to close them in reverse-open order, which hangs
		// (http://docs.oracle.com/javase/tutorial/networking/sockets/readingWriting.html).
//		if(socketReader != null)
//			socketReader.close();
//		if(socketWriter != null)
//			socketWriter.close();
		if(socket != null)	{
			socket.close();
			consoleWriter.println("Socket closed.");
		}
		socketReader = null;
		socketWriter = null;
		socket = null;
		consoleWriter.flush();
		consoleWriter = null;

		try {
			if(thread != null)	{
				thread.join();
				thread = null;
			}
		} catch (InterruptedException e) {
			assert(false);		// nonsense
		}
	}
	
	public static SMSocket connectToServer(SMSolver smsolver, String[] args, PrintWriter consoleWriter)	{
		String bridgeIP = DEFAULT_BRIDGE_IP;
		int bridgePort = DEFAULT_BRIDGE_PORT;

		try	{
			bridgeIP = args[0];
			bridgePort = Integer.valueOf(args[1]);
		} catch(Exception e)	{
		}

		try {
			Socket socket = new Socket(bridgeIP, bridgePort);
			PrintWriter socketWriter = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			consoleWriter.println("Connected to server: "+bridgeIP+":"+bridgePort);
			consoleWriter.flush();
			return new SMSocket(smsolver, socket, socketReader, socketWriter, consoleWriter);
		} catch (UnknownHostException e) {
			consoleWriter.println("ERROR: Hostname " + bridgeIP + " could not be resolved.");
			return null;
		} catch (IOException e)	{
			consoleWriter.println("WARNING: Failed to establish a connection to the SMBridge.");
			return new SMSocket(smsolver, consoleWriter);
		}
	}
}