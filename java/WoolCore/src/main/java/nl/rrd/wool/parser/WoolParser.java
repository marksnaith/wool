package nl.rrd.wool.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nl.rrd.wool.exception.LineNumberParseException;
import nl.rrd.wool.io.LineColumnNumberReader;
import nl.rrd.wool.model.WoolDialogue;
import nl.rrd.wool.model.WoolNode;
import nl.rrd.wool.model.WoolNodeBody;
import nl.rrd.wool.model.WoolNodeHeader;
import nl.rrd.wool.model.nodepointer.WoolNodePointerInternal;
import nl.rrd.wool.parser.WoolNodeState.NodePointerToken;

public class WoolParser {
	public static final String NODE_NAME_REGEX = "[A-Za-z0-9_-]+";
	
	private String dialogueName;
	private LineColumnNumberReader reader;
	
	private WoolDialogue dialogue = null;
	private List<NodePointerToken> nodePointerTokens = null;
	
	public WoolParser(String filename) throws FileNotFoundException {
		this(new File(filename));
	}
	
	public WoolParser(File file) throws FileNotFoundException {
		init(file);
	}
	
	public WoolParser(String dialogueName, InputStream input) {
		init(dialogueName, input);
	}
	
	public WoolParser(String dialogueName, Reader reader) {
		init(dialogueName, new LineColumnNumberReader(reader));
	}
	
	private void init(File file) throws FileNotFoundException {
		String name = file.getName();
		int extSep = name.lastIndexOf('.');
		if (extSep != -1)
			name = name.substring(0, extSep);
		init(name, new FileInputStream(file));
	}
	
	private void init(String dialogueName, InputStream input) {
		try {
			init(dialogueName, new InputStreamReader(input, "UTF-8"));
		} catch (UnsupportedEncodingException ex) {
			throw new RuntimeException("UTF-8 not supported: " +
					ex.getMessage(), ex);
		}
	}
	
	private void init(String dialogueName, Reader reader) {
		this.dialogueName = dialogueName;
		if (reader instanceof LineColumnNumberReader) {
			this.reader = (LineColumnNumberReader)reader;
		} else if (reader instanceof BufferedReader) {
			this.reader = new LineColumnNumberReader(reader);
		} else {
			this.reader = new LineColumnNumberReader(
					new BufferedReader(reader));
		}
	}
	
	public void close() throws IOException {
		reader.close();
	}
	
	public WoolDialogue readDialogue() throws LineNumberParseException,
			IOException {
		if (!dialogueName.matches("[A-Za-z0-9_-]+")) {
			throw new LineNumberParseException("Invalid dialogue name: " +
					dialogueName, 1, 1);
		}
		WoolDialogue result = new WoolDialogue(dialogueName);
		dialogue = result;
		nodePointerTokens = new ArrayList<>();
		WoolNode node;
		while ((node = readNode()) != null) {
			result.addNode(node);
		}
		if (!dialogue.nodeExists("Start")) {
			throw new LineNumberParseException(
					"Node with title \"Start\" not found",
					reader.getLineNum(), reader.getColNum());
		}
		for (WoolNodeState.NodePointerToken pointerToken : nodePointerTokens) {
			if (!(pointerToken.getPointer() instanceof WoolNodePointerInternal))
				continue;
			WoolNodePointerInternal pointer =
					(WoolNodePointerInternal)pointerToken.getPointer();
			if (pointer.getNodeId().toLowerCase().equals("end") ||
					dialogue.nodeExists(pointer.getNodeId())) {
				continue;
			}
			WoolBodyToken token = pointerToken.getToken();
			throw new LineNumberParseException(
					"Found reply with pointer to non-existing node: " +
					pointer.getNodeId(), token.getLineNum(), token.getColNum());
		}
		dialogue = null;
		nodePointerTokens = null;
		return result;
	}
	
	private WoolNode readNode() throws LineNumberParseException, IOException {
		Map<String,String> headerMap = new LinkedHashMap<>();
		boolean inHeader = true;
		int lineNum = reader.getLineNum();
		String line = readLine();
		while (line != null && inHeader) {
			if (getContent(line).equals("---")) {
				inHeader = false;
			} else {
				parseHeaderLine(headerMap, line, lineNum);
				lineNum = reader.getLineNum();
				line = readLine();
			}
		}
		if (inHeader) {
			if (headerMap.isEmpty())
				return null;
			throw new LineNumberParseException(
					"Found incomplete node at end of file",
					reader.getLineNum(), reader.getColNum());
		}
		WoolNodeHeader header = createHeader(headerMap, lineNum);
		boolean inBody = true;
		WoolBodyTokenizer tokenizer = new WoolBodyTokenizer();
		lineNum = reader.getLineNum();
		line = readLine();
		List<WoolBodyToken> bodyTokens = new ArrayList<>();
		while (line != null && inBody) {
			if (getContent(line).equals("===")) {
				inBody = false;
			} else {
				bodyTokens.addAll(tokenizer.readBodyTokens(line + "\n",
						lineNum));
				lineNum = reader.getLineNum();
				line = readLine();
			}
		}
		WoolNodeState nodeState = new WoolNodeState();
		WoolBodyParser bodyParser = new WoolBodyParser(nodeState);
		WoolNodeBody body = bodyParser.parse(bodyTokens,
				Arrays.asList("action", "if", "set"));
		nodePointerTokens.addAll(nodeState.getNodePointerTokens());
		return new WoolNode(header, body);
	}
	
	private void parseHeaderLine(Map<String,String> headerMap, String line,
			int lineNum) throws LineNumberParseException {
		int commentSep = line.indexOf("//");
		if (commentSep != -1)
			line = line.substring(0, commentSep);
		if (line.trim().isEmpty())
			return;
		int sep = line.indexOf(':');
		if (sep == -1) {
			throw new LineNumberParseException(
					"Character : not found in header line", lineNum, 1);
		}
		String keyUntrimmed = line.substring(0, sep);
		String key = keyUntrimmed.trim();
		int keyIndex = 1;
		if (!key.isEmpty())
			keyIndex += skipWhitespace(keyUntrimmed, 0);
		String valueUntrimmed = line.substring(sep + 1);
		String value = valueUntrimmed.trim();
		int valueIndex = sep + 2;
		if (!value.isEmpty())
			valueIndex += skipWhitespace(valueUntrimmed, 0);
		if (key.length() == 0) {
			throw new LineNumberParseException("Found empty header name",
					lineNum, 0);
		}
		if (headerMap.containsKey(key)) {
			throw new LineNumberParseException("Found duplicate header: " + key,
					lineNum, keyIndex);
		}
		if (key.equals("title")) {
			if (!value.matches(NODE_NAME_REGEX)) {
				throw new LineNumberParseException(
						"Invalid node title: " + value, lineNum, valueIndex);
			}
			if (dialogue.nodeExists(value)) {
				throw new LineNumberParseException(
						"Found duplicate node title: " + value, lineNum,
						valueIndex);
			}
		} else if (key.equals("speaker")) {
			if (value.length() == 0) {
				throw new LineNumberParseException("Found empty speaker",
						lineNum, valueIndex);
			}
		}
		headerMap.put(key, value);
	}
	
	private WoolNodeHeader createHeader(Map<String,String> headerMap,
			int lineNum) throws LineNumberParseException {
		List<String> requiredHeaders = Arrays.asList("title", "speaker");
		Map<String,String> optionalTags = new LinkedHashMap<>(headerMap);
		for (String requiredHeader : requiredHeaders) {
			if (!headerMap.containsKey(requiredHeader)) {
				throw new LineNumberParseException(String.format(
						"Required header \"%s\" not found", requiredHeader),
						lineNum, 1);
			}
			optionalTags.remove(requiredHeader);
		}
		WoolNodeHeader header = new WoolNodeHeader(headerMap.get("title"),
				optionalTags);
		header.setSpeaker(headerMap.get("speaker"));
		return header;
	}
	
	/**
	 * Removes a possible comment, and leading and trailing white space from a
	 * string. This should not be used for body lines, because this method does
	 * not check whether a comment marker (//) is inside a string literal.
	 *
	 * @param s the string or null
	 * @return the content or null
	 */
	private String getContent(String s) {
		if (s == null)
			return null;
		int commentIndex = s.indexOf("//");
		if (commentIndex != -1)
			s = s.substring(0, commentIndex);
		return s.trim();
	}
	
	/**
	 * Reads whitespace characters from the specified index and returns the
	 * number of characters read.
	 * 
	 * @param s the string
	 * @param start the start index
	 * @return the number of whitespace characters
	 */
	public static int skipWhitespace(String s, int start) {
		int result = 0;
		for (int i = start; i < s.length(); i++) {
			char c = s.charAt(i);
			if (!Character.isWhitespace(c))
				return result;
			result++;
		}
		return result;
	}
	
	private String readLine() throws IOException {
		StringBuilder builder = new StringBuilder();
		boolean foundCR = false;
		while (true) {
			if (foundCR) {
				Object restoreState = reader.getRestoreState(1);
				int c = reader.read();
				if (c == -1 || c == '\n')
					reader.clearRestoreState(restoreState);
				else
					reader.restoreState(restoreState);
				return builder.toString();
			}
			int c = reader.read();
			if (c == -1) {
				if (builder.length() == 0)
					return null;
				else
					return builder.toString();
			} else if (c == '\n') {
				return builder.toString();
			} else if (c == '\r') {
				foundCR = true;
			} else {
				builder.append((char)c);
			}
		}
	}
}