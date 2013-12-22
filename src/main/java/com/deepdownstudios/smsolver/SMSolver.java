package com.deepdownstudios.smsolver;

import java.io.IOException;
import java.io.PrintWriter;

import jline.console.ConsoleReader;
import jline.console.completer.FileNameCompleter;
import jline.console.completer.StringsCompleter;

/**
 * Processes information entered in console or obtained through a required SMBridge host.
 */
public class SMSolver 
{
	History history = new History();

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

	public static void usage() {
		System.out.println("Usage: java " + SMSolver.class.getName() + " [smbridge_ip smbridge_port]\n" +
				"\twhere smbridge_ip and smbridge_port are the IP address (default: localhost) and port\n" +
				"(default: 9296), respectively, of the optional machine running smbridge.");
	}

	public static void main(String[] args) throws IOException {
		assert args != null;
		if(args.length == 1 && ((args[0].equals("--help")) || (args[0].equals("-h"))))	{
			usage();
			return;
		}
    	
		ConsoleReader reader = new ConsoleReader();
		reader.setPrompt("smsolver> ");
		reader.addCompleter(new FileNameCompleter());
		reader.addCompleter(new StringsCompleter( Command.keywords ));
		
		PrintWriter consoleWriter = new PrintWriter(reader.getOutput());
		SMSolver smsolver = new SMSolver();
		SMSocket smsocket = SMSocket.connectToServer(smsolver, args, consoleWriter);
		if(smsocket == null)
			return;
		
		try	{
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
					break;
				}
				// Compute and send the result to the WebSocket client unless it was 
				// an error, in which case we just log it to the consoleWriter.
				CommandResult result;
				try	{
					result = smsolver.execute(line);
				} catch (CommandException e) {
					consoleWriter.println("ERROR: Command Failed.  " + e.getMessage());
					continue;
				}
				smsocket.sendToWebSocket(result);
			}
		} finally {
			consoleWriter.println();
			smsocket.close();
		}
    }
}
