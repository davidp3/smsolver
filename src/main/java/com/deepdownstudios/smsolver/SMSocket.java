package com.deepdownstudios.smsolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import com.deepdownstudios.smbridge.Endpoint;

public class SMSocket implements Endpoint {
	public Socket socket = null;
	public BufferedReader socketReader = null;
	public PrintWriter socketWriter = null;
	public PrintWriter consoleWriter = null;
	public Thread thread = null;
	protected Endpoint endpoint = null;

	private static final String BEGIN_TAG = "BEGIN";
	private static final String END_TAG = "END";
	private static final String EOL = "\n";
	
	public void start() {
		assert endpoint != null && socket != null && socketReader != null && socketWriter != null && consoleWriter != null;
		
		// Run a thread that reconstructs messages from the SMBridge.  Exceptions are logged but do not
		// interrupt running unless the exception indicates that the connection to the SMBridge is broken.
		thread = new Thread( new Runnable() {
			public void run() {
				while (true) {
					try {
						String line = socketReader.readLine();
						if(line == null)
							break;			// EOF.  Comes when the other thread calls shutdownInput()
						if (!line.equals(BEGIN_TAG))
							System.err.println("WARNING: Malformed message.  Start tag missing.  Received:\n" + line);
						else
							line = ""; // only keep data if it wasnt start tag
	
						StringBuffer message = new StringBuffer();
						while (!line.equals(END_TAG)) {
							message = message.append(line);
							line = socketReader.readLine();
						}
						if(endpoint != null)
							endpoint.process(message.toString());
						else
							System.err.println("WARNING: Received message from SMBridge but it is the only endpoint: \n" + line);
					} catch(SocketException e) {
						if("Socket closed".equals(e.getMessage()))	{
							consoleWriter.println(e.getMessage());
							break;		// main thread terminated us
						}
					} catch (IOException e) {
						System.err.println("WARNING: Exception while reading from bridge: " + e);
						e.printStackTrace();
					}
				}
			}
		});
		thread.start();
	}
	
	public synchronized void process(String result) {
		if(socketWriter == null)
			return;
		socketWriter.print(BEGIN_TAG + EOL + result + EOL + END_TAG + EOL);
		socketWriter.flush();
	}

	public synchronized void close() {
		// The interrupt should cause socketReader.readLine to throw an Exception but it doesn't.
		// See http://stackoverflow.com/questions/3595926/how-to-interrupt-bufferedreaders-readline
		// This answer was the least bad.
		try {
			if(socket != null)
				socket.shutdownInput();
		} catch (IOException e) {
			consoleWriter.println("ERROR: Problem closing input on socket: " + e.getMessage());
			return;
		}
		try {
			if(thread != null)	{
				thread.interrupt();
				thread.join();
				thread = null;
			}
		} catch (InterruptedException e) {
			consoleWriter.println("ERROR: Problem closing input on socket: " + e.getMessage());
			return;
		}
		
		// socketReader.close() then hangs.  Note that the docs conflict on what to do here.  The Socket Javadoc says that
		// closing the reader or writer closes the whole socket (implying socket.close() any one close() alone is fine).
		// The JavaSE book, OTOH, suggests that you *need* to close them in reverse-open order, which hangs
		// (http://docs.oracle.com/javase/tutorial/networking/sockets/readingWriting.html).
//		if(socketReader != null)
//			socketReader.close();
//		if(socketWriter != null)
//			socketWriter.close();
		if(socket != null)	{
			try {
				socket.close();
				consoleWriter.println("Socket to SMBridge closed.");
			} catch (IOException e) {
				consoleWriter.println("ERROR: Exception while closing connection to SMBridge: " + e.getMessage());
			}
		}
		socketReader = null;
		socketWriter = null;
		socket = null;
		if(consoleWriter != null)
			consoleWriter.flush();
		consoleWriter = null;
	}
	
	public SMSocket(String bridgeIP, int bridgePort, PrintWriter consoleWriter) throws IOException	{
		socket = new Socket(bridgeIP, bridgePort);
		socketWriter = new PrintWriter(socket.getOutputStream(), true);
		socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		this.consoleWriter = consoleWriter;
	}

	public void setConnectedEndpoint(Endpoint endpoint) {
		this.endpoint = endpoint;
	}
}