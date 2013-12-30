package com.deepdownstudios.smsolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.MarshalException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import alice.tuprolog.Term;

import com.deepdownstudios.scxml.jaxb.ObjectFactory;
import com.deepdownstudios.scxml.jaxb.ScxmlScxmlType;
import com.deepdownstudios.smsolver.Command.REPLCommand;
import com.deepdownstudios.smsolver.Command.SingleCommand;
import com.deepdownstudios.smsolver.History.HistoryException;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

public class ScxmlFile {
	private String filenameWithSuffix;
	private String filenameBase;		///< Suffix-less filename
	// NOTE: At least one of scxml and scxmlProlog is not null
	private ScxmlScxmlType scxml;		/// SCXML document as XML
	private List<Term> scxmlProlog;		/// SCXML document as prolog term(s)
	private String statemachineName;

	public static final String SCXML_SUFFIX = "scxml";
	public static final String LPSCR_SUFFIX = "lpscr";
	private static final String LPSCR_SCXML_FILE_TAG = "scxmlfile";
	private static final String LPSCR_BLOCK_DELIMETER = "---";
	private static final String LPSCR_EMBEDDED_SCXML_TAG = "scxml";


	/**
	 * Create empty SCXML document
	 * @param filename
	 */
	public ScxmlFile(String filename)	{
		this(filename, getNewScxmlDocument(Files.getNameWithoutExtension(filename)));
	}

	/**
	 * Create a 'file-object' for a SCXML document
	 * @param filename		Full filename, including either .scxml or .lpscr suffix
	 * @param scxml			JAXB SCXML document
	 */
	public ScxmlFile(String filename, ScxmlScxmlType scxml)	{
		assert filename != null && scxml != null;
		setFilename(filename);
		this.statemachineName = scxml.getName();
		assert this.statemachineName != null;
		this.scxml = scxml;
		this.scxmlProlog = null;
	}

	/**
	 * Create a 'file-object' for a SCXML document
	 * @param filename			Full filename, including either .scxml or .lpscr suffix
	 * @param statemachineName	Name of SCXML state machine
	 * @param scxmlProlog		Prolog terms that constitute document
	 */
	public ScxmlFile(String filename, String statemachineName, List<Term> scxmlProlog)	{
		assert filename != null && scxmlProlog != null && statemachineName != null;
		setFilename(filename);
		this.statemachineName = statemachineName;
		this.scxml = null;
		this.scxmlProlog = scxmlProlog;
	}

	/**
	 * @return Filename when saved as .scxml document
	 */
	public String getSCXMLName() {
		return filenameBase + '.' + ScxmlFile.SCXML_SUFFIX;
	}

	/**
	 * @return Filename when saved as .lpscr document
	 */
	public String getLPSCRName() {
		return filenameBase + '.' + ScxmlFile.LPSCR_SUFFIX;
	}
	
	/**
	 * @return	JAXB SCXML document.
	 * @throws CommandException		Error generating JAXB document from Prolog spec
	 */
	public ScxmlScxmlType getScxml() throws CommandException	{
		if(scxml == null)	{
			assert scxmlProlog != null;
			scxml = (new PrologToScxml()).prologToScxml(statemachineName, scxmlProlog);
			assert scxml != null;
		}
		return scxml;
	}

	/**
	 * @return	Prolog terms that define SCXML document
	 * @throws CommandException		Error generating Prolog terms from JAXB
	 */
	public List<Term> getScxmlProlog() throws CommandException {
		if(scxmlProlog == null)	{
			assert scxml != null;
			scxmlProlog = ScxmlToProlog.scxmlToProlog(scxml);
			assert scxmlProlog != null; 
		}
		return scxmlProlog;
	}

	/**
	 * Get the name of the file as it was specified when the file was loaded/created.  This
	 * may be either scxml or lpscr (but it is one of the two).
	 */
	public String getFilename() {
		return filenameWithSuffix;
	}

	/**
	 * Factory that creates new SCXML documents.
	 * @param singleCommand		A REPLCommand.NEW command with one parameter that is the SCXML filename/document name
	 * @return		The new SCXML document
	 * @throws CommandException		The singleCommand wasn't a new(Filename)/1 command.
	 */
	public static ScxmlFile newState(SingleCommand singleCommand) throws CommandException {
		assert singleCommand.getREPLCommand() == REPLCommand.NEW;
		if(singleCommand.getParameters().size() != 1)
			throw new CommandException("Syntax error in '" + singleCommand.toString() + "'.  Expected format: new(filename)");

		String filename = singleCommand.getParameters().get(0).toString();
		String ext = Files.getFileExtension(filename).toLowerCase();
		// Make sure filename has lpscr (default) or scxml suffix.
		if(!ext.equals(ScxmlFile.LPSCR_SUFFIX) && !ext.equals(ScxmlFile.SCXML_SUFFIX))	{
			filename = filename + "." + ScxmlFile.LPSCR_SUFFIX;
		}
		
		return new ScxmlFile(filename);
	}

	/**
	 * Factory to load an .lpscr or .scxml file.
	 * @param history		History to load file into
	 * @param singleCommand	load/0 command or load(Filename)/1 command
	 * @return				The newly loaded SCXML document
	 * @throws CommandException		The command was improperly formatted or the file failed to load
	 */
	public static ScxmlFile load(History history, SingleCommand singleCommand) throws CommandException {
		String filename, 
			requestedName;		// For error messages
		File file;
		List<Term> parameters = singleCommand.getParameters();
		boolean asScxml;		// either scxml or lpscr
		if(parameters.isEmpty())	{
			filename = history.getCurrentState().getScxmlFile().getLPSCRName();
			requestedName = Files.getNameWithoutExtension(filename);
			asScxml = false;
			file = new File(filename);
			if(!file.isFile())	{
				// Try scxml
				filename = history.getCurrentState().getScxmlFile().getSCXMLName();
				file = new File(filename);
				asScxml = true;
			}
		}
		else if(parameters.size() == 1)	{
			filename = parameters.get(0).toUnquotedString();
			String suffix = Files.getFileExtension(filename);
			if(!ScxmlFile.LPSCR_SUFFIX.equals(suffix) && !ScxmlFile.SCXML_SUFFIX.equals(suffix))	{
				filename = filename + '.' + ScxmlFile.LPSCR_SUFFIX;
				asScxml = false;
			}
			else
				asScxml = ScxmlFile.SCXML_SUFFIX.equals(suffix);
			file = new File(filename);
			requestedName = filename;
		}
		else
			throw new CommandException("Syntax error: '" + singleCommand.toString() + "'.  Format is load. or load(filename).");
		
		if(!file.isFile())
			throw new CommandException("File '" + requestedName + "' not found.");

		if(asScxml)
			return loadScxml(file);
		return loadLpscr(file);
	}
	/**
	 * Save scxml or lpscr file.  If saving as SCXML then the current state is written.  If saving as lpscr
	 * then the lpscr file will contain the state at the last load or new command before the current state
	 * as well as all of the lpscr commands issued between the load/new and the current state.
	 * @param history			Current state history
	 * @param singleCommand		The save command.  If it has no parameters or the filename parameter does
	 * 							not specify .scxml or .lpscr then this function saves as lpscr.
	 * @return					The name of the file written.
	 * @throws CommandException	The command was incorrectly formatted or the save failed.
	 */
	public static String save(History history, SingleCommand singleCommand) throws CommandException {
		try	{
			history.getCurrentState();
		} catch (HistoryException e)	{
			throw new CommandException("Failed to save.  There is no document present.  You must 'load' or 'new' a document first.");
		}
		
		String filename;
		List<Term> parameters = singleCommand.getParameters();
		boolean asScxml;		// either scxml or lpscr
		if(parameters.isEmpty())	{
			filename = history.getCurrentState().getScxmlFile().getLPSCRName();
			asScxml = false;
		}
		else if(parameters.size() == 1)	{
			filename = parameters.get(0).toUnquotedString();
			String suffix = Files.getFileExtension(filename);
			if(!ScxmlFile.LPSCR_SUFFIX.equals(suffix) && !ScxmlFile.SCXML_SUFFIX.equals(suffix))	{
				filename = filename + '.' + ScxmlFile.LPSCR_SUFFIX;
				asScxml = false;
			}
			else
				asScxml = ScxmlFile.SCXML_SUFFIX.equals(suffix);
		}
		else
			throw new CommandException("Syntax error: '" + singleCommand.toString() + "'.  Format is save. or save(filename).");
		
		if(asScxml)
			saveScxml(history.getCurrentState().getScxmlFile().getScxml(), filename);
		else
			saveLpscr(history, filename);
		return filename;
	}



	private static ScxmlScxmlType getNewScxmlDocument(String name)	{
		ScxmlScxmlType ret = new ScxmlScxmlType();
		ret.setName(name);
		// TODO: Technically should have at least one state, parallel or final child but I wont
		// tell if JAXB2 wont.
		return ret;
	}
	

	private static ScxmlFile loadLpscr(File file) throws CommandException {
		// Open file
		Scanner scanner;
		try {
			scanner = new Scanner(file);
		} catch (FileNotFoundException e) {
			throw new CommandException("File '" + file.getAbsolutePath() + "' was not found.");
		}
		
		try	{
			// First line is: 'scxmlfile "filename"' (the single quotes are not present).
			// or 'scxml <the embedded scxml document>'
			String scxmlTag = scanner.next();
			State fakeState;
			if(LPSCR_SCXML_FILE_TAG.equals(scxmlTag))	{
				// This is unused and problematic.  The .scxml file can get out of sync with the lpscr file.
				String scxmlFilename = scanner.nextLine();		// presumably, this skips the word we already read
				// Filename is everything between single-quotes
				scxmlFilename = scxmlFilename.substring(scxmlFilename.indexOf('\''), scxmlFilename.lastIndexOf('\''));
				ScxmlFile scxmlFile = loadScxml(new File(scxmlFilename));
				fakeState = new State(Command.NOOP, "Loaded SCXML File '" + scxmlFilename +
						"' referenced in '" + file.getPath() + "'", scxmlFile);
			}
			else if(LPSCR_EMBEDDED_SCXML_TAG.equals(scxmlTag))	{
				StringBuilder embeddedXml = new StringBuilder();
				String line = scanner.nextLine();
				while(!LPSCR_BLOCK_DELIMETER.equals(line))	{
					embeddedXml.append(line);
					line = scanner.nextLine();
				}
				ScxmlFile scxmlFile = loadScxml(file.getPath(), embeddedXml.toString());
				// Command is NOOP but we throw away this history in this function so it'll never come up.
				fakeState = new State(Command.NOOP, "Loaded SCXML Document referenced in '" + file.getPath() + "'", scxmlFile);
			}
			else	
				throw new CommandException("scxml tag missing from file '" + file.getPath() + "'.");
			
			// The rest of the lines are lpscr command blocks, broken into groups, each 
			// followed by lines that are just three dashes (ie '---'), including the final block of commands.
			// Issue each block as a Command to build a history
			History fakeHistory = new History(ImmutableList.<State>of(fakeState), 0);
			assert fakeState != null;
			while(scanner.hasNext())	{
				StringBuffer commandStrBuf = new StringBuffer();
				boolean done = false;
				while(!done)	{
					String nextLine = scanner.nextLine();
					if(nextLine.equals(LPSCR_BLOCK_DELIMETER))
						break;		// read entire block of commands
					commandStrBuf.append(nextLine);
				}
				String commandStr = commandStrBuf.toString();
				Commands commands = Commands.parse(commandStr);
				CommandResult result = commands.execute(fakeHistory);
				fakeHistory = result.getHistory();
			}
			return fakeHistory.getCurrentState().getScxmlFile();
		} catch(NoSuchElementException e)	{
			throw new CommandException("Syntax error in lpscr file", e);
		} finally {
			scanner.close();
		}
	}

	@SuppressWarnings("unchecked")
	private static ScxmlFile loadScxml(File file) throws CommandException {
		Unmarshaller unmarshaller = getScxmlUnmarshaller();
		ScxmlScxmlType scxml;
		try {
			scxml = ((JAXBElement<ScxmlScxmlType>)unmarshaller.unmarshal(file)).getValue();
		} catch (UnmarshalException e) {
			throw new CommandException("'" + file.getAbsolutePath() + "' is not a valid SCXML file.", e);
		} catch (JAXBException e) {
			throw new CommandException("BUG: SCXML JAXB unmarshaller failed.", e);
		}
		return new ScxmlFile(file.getPath(), scxml);
	}

	/**
	 * Create a State from an SCXML string.
	 * @param filename		Name of file to associate with the SCXML document.  Must have either .scxml or .lpscr suffix.
	 * @param scxmlDocument	The XML document as string
	 * @return				The new state
	 * @throws CommandException
	 */
	@SuppressWarnings("unchecked")
	private static ScxmlFile loadScxml(String filename, String scxmlDocument) throws CommandException {
		Unmarshaller unmarshaller = getScxmlUnmarshaller();
		ScxmlScxmlType scxml;
		try {
			scxml = ((JAXBElement<ScxmlScxmlType>)unmarshaller.unmarshal(
							new StreamSource( new StringReader( scxmlDocument ) ))).getValue();
		} catch (UnmarshalException e) {
			throw new CommandException("'" + filename + "' does not reference valid SCXML contents.", e);
		} catch (JAXBException e) {
			throw new CommandException("BUG: SCXML JAXB unmarshaller failed.", e);
		}
		return new ScxmlFile(filename, scxml);
	}

	private static Unmarshaller getScxmlUnmarshaller() throws CommandException {
		JAXBContext context;
		try {
			context = JAXBContext.newInstance("com.deepdownstudios.scxml.jaxb");
		} catch (JAXBException e) {
			throw new CommandException("BUG: JAXB was unable to initialize namespace 'com.deepdownstudios.scxml.jaxb'", e);
		}
		
		try {
			return context.createUnmarshaller();
		} catch (JAXBException e) {
			throw new CommandException("BUG: Could not create SCXML JAXB unmarshaller.", e);
		}
	}


	/**
	 * Saves an lpscr file using the embeddedd scxml format - ie the SCXML file is embedded in the lpscr file.
	 * @param history	History to save.  Everything going back to the last load/new command is written.
	 * @param filename	Name of file to save to.  Must end in .lpscr.
	 * @throws CommandException 
	 */
	private static void saveLpscr(History history, String filename) throws CommandException {
		assert ScxmlFile.LPSCR_SUFFIX.equals(Files.getFileExtension(filename));
		List<State> states = history.getStates();
		int currentStateIndex = history.getCurrentStateIndex();
		states = states.subList(0, currentStateIndex+1);
		int lastDeserializeState = currentStateIndex;
		Command command = states.get(lastDeserializeState).getCommand();
		while(!command.isLoad() && !command.isNew())	{
			lastDeserializeState--;
			if(lastDeserializeState < 0)	{
				throw new CommandException("BUG: Non-empty history does not contain a load or new command.");	// should be impossible
			}
			command = states.get(lastDeserializeState).getCommand();
		}

		Marshaller marshaller = getScxmlMarshaller();
		PrintWriter writer = new PrintWriter(getFileOutputStream(filename));
		writer.println(LPSCR_EMBEDDED_SCXML_TAG);
		try	{	
			marshaller.marshal(new ObjectFactory().createScxml(states.get(lastDeserializeState).getScxmlFile().getScxml()), writer);
			writer.println(LPSCR_BLOCK_DELIMETER);
			for(int itState = lastDeserializeState+1; itState < states.size(); itState++)	{
				State state = states.get(itState);
				writer.println(state.getCommand().toString());
				writer.println(LPSCR_BLOCK_DELIMETER);
			}
			if(writer.checkError())	{
				throw new CommandException("I/O error while writing '" + filename + "'.");
			}
		} catch (MarshalException e) {
			throw new CommandException("BUG: DOM failed marshalling: '" + e.getMessage() + "'.", e);
		} catch (JAXBException e) {
			throw new CommandException("BUG: Could not marshal SCXML DOM.", e);
		} finally {
			writer.close();
			if(writer.checkError())	{
				throw new CommandException("I/O error while trying to close file '" + filename + "'.");
			}
		}
	}

	private static void saveScxml(ScxmlScxmlType scxml, String filename) throws CommandException {
		Marshaller marshaller = getScxmlMarshaller();
		FileOutputStream fileOutputStream = getFileOutputStream(filename);
		try	{	
			marshaller.marshal(new ObjectFactory().createScxml(scxml), fileOutputStream);
		} catch (MarshalException e) {
			e.printStackTrace();
			throw new CommandException("BUG: DOM failed marshalling: '" + e.getMessage() + "'.", e);
		} catch (JAXBException e) {
			throw new CommandException("BUG: Could not marshal SCXML DOM.", e);
		} finally {
			try	{
				fileOutputStream.close();
			} catch (IOException e) {
				throw new CommandException("I/O error while trying to close file '" + filename + "'.", e);
			}
		}
	}

	private static FileOutputStream getFileOutputStream(String filename) throws CommandException {
		FileOutputStream fileOutputStream;
		try {
			fileOutputStream = new FileOutputStream(filename);
		} catch (FileNotFoundException e) {
			throw new CommandException("I/O error while trying to create file '" + filename + "'.", e);
		}
		return fileOutputStream;
	}

	private static Marshaller getScxmlMarshaller() throws CommandException {
		JAXBContext context;
		try {
			context = JAXBContext.newInstance("com.deepdownstudios.scxml.jaxb");
		} catch (JAXBException e) {
			throw new CommandException("BUG: JAXB was unable to initialize namespace 'com.deepdownstudios.scxml.jaxb'", e);
		}
		
		Marshaller marshaller;
		try {
			marshaller = context.createMarshaller();
		} catch (JAXBException e) {
			throw new CommandException("BUG: Could not create SCXML JAXB marshaller.", e);
		}
		try {
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		} catch (PropertyException e1) {
			throw new CommandException("BUG: Marshaller JAXB property error: '" + e1.getMessage() + "'.", e1);
		}
		return marshaller;
	}

	public String getScxmlAsString() throws CommandException {
		Marshaller marshaller = getScxmlMarshaller();
		StringWriter ret = new StringWriter();
		try	{	
			marshaller.marshal(new ObjectFactory().createScxml(scxml), ret);
		} catch (MarshalException e) {
			e.printStackTrace();
			throw new CommandException("BUG: DOM failed marshalling to string: '" + e.getMessage() + "'.", e);
		} catch (JAXBException e) {
			throw new CommandException("BUG: Could not marshal SCXML DOM to string.", e);
		}
		return ret.toString();
	}

	// Constructor helper
	private void setFilename(String filename) {
		assert filename != null && scxml != null;
		assert LPSCR_SUFFIX.equals(Files.getFileExtension(filename)) || SCXML_SUFFIX.equals(Files.getFileExtension(filename)); 
		this.filenameWithSuffix = filename;
		this.filenameBase = Files.getNameWithoutExtension(filename);
	}
}
