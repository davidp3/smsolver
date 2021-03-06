package com.deepdownstudios.smsolver;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import jline.console.ConsoleReader;
import jline.console.completer.FileNameCompleter;
import jline.console.completer.StringsCompleter;
import jline.console.history.FileHistory;
import jline.console.history.PersistentHistory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.deepdownstudios.smbridge.Endpoint;
import com.deepdownstudios.smbridge.WebSocketEndpoint;

/**
 * Processes information entered in console or obtained through a required SMBridge host.
 */
public class SMSolver implements Endpoint
{
	private static int DEFAULT_WS_PORT = 8887;
	private static int DEFAULT_POSIX_PORT = 9296;
	private static final String EOL = "\n";
	private static final String ERROR_TAG = "ERROR";
	private static final String ENDPOINT_RESPONSE_DELIMETER = "---";

	History history = new History();
	private Endpoint endpoint = null;

	public synchronized CommandResult execute(String commandStr) throws CommandException {
		Commands command = Commands.parse(commandStr);
		// Execute the command and update the history to include the result.
		CommandResult ret = command.execute(history);
		history = ret.getHistory();
		return ret;
	}

	/******************************************************************************************/
	/* Main/Driver */
	/******************************************************************************************/

	private static class Args {		// For command-line arguments
		@Parameter(names = { "-i", "--ip" }, description = "IP Address of SMBridge to connect to (if used).")
		public String ipAddr = "localhost";
		@Parameter(names = { "-p", "--port" }, description = "Port of address to connect to (if used).  " + 
				"Default is 8887 for websocket server or 9296 for SMBridge connection.")
		public int port = -1;
		@Parameter(names = { "-w", "--websocket" }, description = "Establish websocket server.  Cannot be used with SMBridge.")
		public boolean useWebsocket = false;
		@Parameter(names = { "-b", "--smbridge" }, description = "Connect to SMBridge server.  Cannot be used with WebSocket")
		public boolean useSMBridge = false;
		@Parameter(names = { "-h", "--help" }, description = "Help with command line arguments", help = true)
		public boolean help = false;
	}
	
	private SMSolver()	{
		// In case we are CTRL+C-ed or something, make sure endpoints are closed.
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				if(endpoint != null)
					endpoint.close();
			}
		}));
	}
	
	public static void main(String[] args) throws IOException {
		assert args != null;

		Args cliArgs = new Args();
		JCommander jcommander = new JCommander(cliArgs, args);
		jcommander.setProgramName("java " + SMSolver.class.getName());
		if(cliArgs.useSMBridge && cliArgs.useWebsocket)	{
			System.err.println("ERROR: Cannot start websocket server and use SMBridge simultaneously.");
			jcommander.usage();
			return;
		}
		if(cliArgs.help)	{
			jcommander.usage();
			return;
		}
		
		ConsoleReader reader = new ConsoleReader();
	    reader.setHistory(new FileHistory(new File(System.getProperty("user.home"), ".smsolver.history")));
		reader.setPrompt("smsolver> ");
		reader.addCompleter(new FileNameCompleter());
		reader.addCompleter(new StringsCompleter( Command.keywords ));
		reader.setHistoryEnabled(true);
		
		PrintWriter consoleWriter = new PrintWriter(reader.getOutput());
		final SMSolver smsolver = new SMSolver();

		if(cliArgs.useWebsocket)	{
			if(cliArgs.port == -1)
				cliArgs.port = DEFAULT_WS_PORT;
			WebSocketEndpoint websocket = new WebSocketEndpoint( cliArgs.port );
			smsolver.setConnectedEndpoint(websocket);
			websocket.setConnectedEndpoint(smsolver);
			websocket.start();
			consoleWriter.println( "WebSocket Server started at address: " + cliArgs.ipAddr + ":" + cliArgs.port );
		} else if(cliArgs.useSMBridge)	{
			if(cliArgs.port == -1)
				cliArgs.port = DEFAULT_POSIX_PORT;
			SMSocket smsocket;
			try	{
				smsocket = new SMSocket(cliArgs.ipAddr, cliArgs.port, consoleWriter);
			} catch (IOException e) {
				consoleWriter.println( "Failed to open socket: " + e.getMessage() );
				throw e;
			}
			smsocket.setConnectedEndpoint(smsolver);
			smsolver.setConnectedEndpoint(smsocket);
			smsocket.start(); 
			consoleWriter.println( "SMBridge connected at address: " + cliArgs.ipAddr + ":" + cliArgs.port );
		}

		String line;
		while ((line = reader.readLine()) != null) {
			if (line.equalsIgnoreCase("quit.") || line.equalsIgnoreCase("exit.")) {
				break;		// Don't want this to persist in 'reader' history.
			}

			// Compute and send the result to the endpoint unless it was
			// an error, in which case we just log it to the consoleWriter (we dont send it to the endpoint).
			try {
				smsolver.executeAndRespond(line);
			} catch (CommandException e) {
				consoleWriter.println("ERROR: Command Failed.  " + e.getMessage());
			}
			
			// JLine2 seems to have the bug that it never saves the history to a file
			// unless you manually do this:
			((PersistentHistory)reader.getHistory()).flush();
		}
		
		// Close the connection to the remote server.  This is necessary to stop those threads which
		// would otherwise keep running forever.
		if(smsolver.endpoint != null)
			smsolver.endpoint.close();
	}

	private void executeAndRespond(String line) throws CommandException {
		CommandResult result = execute(line);
		if (endpoint == null)
			return;
		
		// If there is no valid currentstate then just return the message
		State currentState;
		try	{
			currentState = result.getHistory().getCurrentState();
		} catch(CommandException e)	{
			endpoint.process(result.getMessage());
			return;
		}
		
		StringBuffer message = new StringBuffer(result.getMessage())
				.append(EOL)
				.append(ENDPOINT_RESPONSE_DELIMETER)
				.append(EOL)
				.append(currentState.getScxmlFile().getScxmlAsString());
		endpoint.process(message.toString());
	}

	public void process(String message) {
		// Execute the command we were sent and send back the response. If it
		// results in an error then send the error back.
		try {
			executeAndRespond(message);
		} catch (CommandException e) {
			if (endpoint != null)
				endpoint.process(ERROR_TAG + EOL + e.getMessage());
		}
	}

	public void setConnectedEndpoint(Endpoint endpoint) {
		this.endpoint = endpoint;
	}

	public void close() {	}
}
