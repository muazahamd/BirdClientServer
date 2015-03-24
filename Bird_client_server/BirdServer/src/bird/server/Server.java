package bird.server;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

import bird.model.Bird;
import bird.model.BirdSighting;
import bird.model.Constants;


public class Server {
	
	private static final String PORT_SWITCH = "-port";
	private static final String DATA_SWITCH = "-data";
	private static final String PROC_COUNT_SWITCH = "-proc_count";
	
	private static final int DEFAULT_PROC_COUNT = 2;
	/**
	 * 30 mins gap between two save model call.
	 */
	private static final int SAVE_MODEL_INTERVAL = 1800000;
	
	private static final String DATA_FOLDER = "serverdata";
	private static final String BIRD_FILE = "birds.xml";
	private static final String SIGHTING_FILE = "sightings.xml";
	
	private static final String BIRD_ROOT_NODE = "birds";
	private static final String BIRD_NODE = "bird";
	private static final String BIRD_NAME_ATTRIBUTE = "name";
	private static final String BIRD_COLOR_ATTRIBUTE = "color";
	private static final String BIRD_WEIGHT_ATTRIBUTE = "weight";
	private static final String BIRD_HEIGHT_ATTRIBUTE = "height";
	
	private static final String SIGHTING_ROOT_NODE = "sightings";
	private static final String SIGHTING_BIRD_NODE = "bird";
	private static final String SIGHTING_NODE = "sighting";
	private static final String SIGHTINGS_NAME_ATTRIBUTE = "name";
	private static final String SIGHTINGS_LOCATION_ATTRIBUTE = "location";
	private static final String SIGHTINGS_DATE_ATTRIBUTE = "date";
	
	private final int port;
	private final File serverDataFolder;
	private File birdsFile;
	private File sightingsFile;
	
	private ServerSocket serverSocket;
	
	private Hashtable<String, Bird> model = new Hashtable<String, Bird>();
	private Vector<Socket> requests = new Vector<Socket>();
	
	private volatile boolean shutdown = false;
	private SaveModelThread saveModelThread = new SaveModelThread();
	private WorkerThread[] workerThreads;
		
	public Server(int port, File serverDataFolder, int procCount) {
		this.port = port;
		this.serverDataFolder = serverDataFolder;
		this.workerThreads = new WorkerThread[procCount];
		for(int i = 0; i < workerThreads.length; i++)
			workerThreads[i] = new WorkerThread("Worker - " + i);
	}

	@SuppressWarnings("static-access")
	public void run () {
		/*
		 * Read files and create model. We do not need to get lock on model
		 * because all the threads will be started later.
		 */
		if(!createModel())
			return;
		
		/*
		 * Start save model thread.
		 */
		saveModelThread.start();
		
		/*
		 * Start worker threads.
		 */
		for(WorkerThread wt : workerThreads)
			wt.start();
		
		/*
		 * Create and connect server socket.
		 */
		try {
			serverSocket = new ServerSocket(port);
		} 
		catch (Exception e1) {
			System.err.println("Unable to create server socket, exiting.");
			shutdown = true;
		}
		
		System.out.println("Accepting clients now.");
		
		while(!shutdown) {
			try {
				requests.add(serverSocket.accept());
				
				System.out.println("A client has connected.");
			} 
			catch (SocketException e) {
				System.out.println("Server socket closed.");
			}
			catch (Exception e) {
			}
		}
		
		/*
		 * Shutdown the server.
		 */
		System.out.println("Server Shutdown Has Started...");
		
		/*
		 * Now wait for the worker threads to stop.
		 */
		boolean completed = true;
		do {
			try {
				/*
				 * Wait for sometime before checking.
				 */
				Thread.currentThread().sleep(250);
			} 
			catch (InterruptedException e) {
			}
			
			completed = true;
			for(WorkerThread wt : workerThreads) {
				if(wt.isAlive()) {
					completed = false;
					break;
				}
			}
		} while(!completed);
		
		/*
		 * Now stop the save model thread. We need to interrupt it because it
		 * might be sleeping and it's sleeping time is very long.
		 */
		saveModelThread.interrupt();
		
		/*
		 * Now wait for it to stop.
		 */
		while (saveModelThread.isAlive()) {
			try {
				/*
				 * Wait for sometime before checking.
				 */
				Thread.currentThread().sleep(250);
			} 
			catch (InterruptedException e) {
			}
		}
		
		System.out.println("All Threads have been stopped.");
		
		/*
		 * At the end save model.
		 */
		saveModel();
		
		System.out.println("Server Has Shutdown");
	}

	private boolean createModel() {
		System.out.println("Creating Model ...");
		
		boolean birdsFileCreated = false;
		birdsFile = new File(serverDataFolder.getAbsolutePath() + File.separator + BIRD_FILE);
		if(!birdsFile.exists()) {
			try {
				birdsFile.createNewFile();
				birdsFileCreated = true;
			} 
			catch (IOException e) {
				System.err.println("Unable to create birds.xml file, exiting");
				return false;
			}
		}
		
		sightingsFile = new File(serverDataFolder.getAbsolutePath() + File.separator + SIGHTING_FILE);
		if(birdsFileCreated && sightingsFile.exists()) {
			/*
			 * If the birds file has been created new, delete previous
			 * sightings file and create it new as well.
			 */
			sightingsFile.delete();
		}
		
		if(!sightingsFile.exists()) {
			try {
				sightingsFile.createNewFile();
			} 
			catch (IOException e) {
				System.err.println("Unable to create sightings.xml file, exiting");
				return false;
			}
		}
		
		/*
		 * Only parse it if size is greater than 0.
		 */
		if(birdsFile.length() > 0 && !readBirdsFile())
			return false;
		
		/*
		 * Only parse it if size is greater than 0.
		 */
		if(sightingsFile.length() > 0 && !readSightingsFile())
			return false;
		
		System.out.println("Model Created ...");
		return true;
	}
	
	private boolean readBirdsFile() {
		try {
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(birdsFile);
			doc.getDocumentElement().normalize();

			NodeList birdNodes = doc.getElementsByTagName(BIRD_NODE);
			for (int i = 0; i < birdNodes.getLength(); i++) {

				Node node = birdNodes.item(i);
				if (node.getNodeType() != Node.ELEMENT_NODE)
					continue;
					
				Element birdElement = (Element) node;
				
				/*
				 * Get name, if null or empty, skip it.
				 */
				String name = birdElement.getAttribute(BIRD_NAME_ATTRIBUTE);
				if(name == null || name.isEmpty()) {
					System.err.println("'" + BIRD_NAME_ATTRIBUTE + "' is missing or contains empty value, skipping.");
					continue;
				}
					
				String color = birdElement.getAttribute(BIRD_COLOR_ATTRIBUTE);
				float weight = 0;
				float height = 0;
				
				/*
				 * Parse weight.
				 */
				String value = birdElement.getAttribute(BIRD_WEIGHT_ATTRIBUTE);
				try {
					weight = Float.parseFloat(value);
				}
				catch(NumberFormatException e){
					System.err.println("'" + BIRD_WEIGHT_ATTRIBUTE + "' attribute does not contain valid value for bird '" + name + "'.");
				}
				
				/*
				 * Parse height.
				 */
				value = birdElement.getAttribute(BIRD_HEIGHT_ATTRIBUTE);
				try {
					height = Float.parseFloat(value);
				}
				catch(NumberFormatException e){
					System.err.println("'" + BIRD_HEIGHT_ATTRIBUTE + "' attribute does not contain valid value for bird '" + name + "'.");
				}
				
				if(model.containsKey(name)) {
					System.err.println("'" + name + "' bird is already present, skipping.");
					continue;
				}
					
				model.put(name, new Bird(name, color, weight, height));
			}
		} 
		catch (SAXParseException err) {
			System.err.println("Parsing error:" + " line - " + err.getLineNumber() + ", uri - " + err.getSystemId());
			System.err.println("Message: " + err.getMessage());
			return false;
		} 
		catch (Exception e) {
			System.err.println(e.getMessage());
			return false;
		}
		
		return true;
	}

	private boolean readSightingsFile() {
		try {
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(sightingsFile);
			doc.getDocumentElement().normalize();

			NodeList birdNodes = doc.getElementsByTagName(SIGHTING_BIRD_NODE);
			for (int i = 0; i < birdNodes.getLength(); i++) {

				Node birdNode = birdNodes.item(i);
				if (birdNode.getNodeType() != Node.ELEMENT_NODE)
					continue;
					
				Element birdElement = (Element) birdNode;
				
				/*
				 * Get name attribute. If name is empty/missing, skip this node.
				 */
				String name = birdElement.getAttribute(SIGHTINGS_NAME_ATTRIBUTE);
				if(name == null || name.isEmpty()) {
					System.err.println("Sighting record with empty/missing bird name, skipping.");
					continue;
				}
				
				/*
				 * Get bird object from the model. If it is not present, skip this node.
				 */
				Bird bird = model.get(name);
				if(bird == null) {
					System.err.println("Bird '" + name + "' does not present in birds list, skipping.");
					continue;
				}
				
				/*
				 * Get sighting nodes.
				 */
				NodeList sightingNodes = birdElement.getElementsByTagName(SIGHTING_NODE);
				for(int j = 0; j < sightingNodes.getLength(); j++) {
					Node sightingNode = sightingNodes.item(j);
					if(sightingNode.getNodeType() != Node.ELEMENT_NODE)
						continue;
					
					Element sightingElement = (Element) sightingNode;
					
					/*
					 * Get location.
					 */
					String location = sightingElement.getAttribute(SIGHTINGS_LOCATION_ATTRIBUTE);
					Date date = null;
					
					/*
					 * Parse weight.
					 */
					String value = sightingElement.getAttribute(SIGHTINGS_DATE_ATTRIBUTE);
					if(validateDateAndTime(value))
						date = DateFormat.getInstance().parse(value);
					else
						System.err.println("'" + SIGHTINGS_DATE_ATTRIBUTE + "' attribute does not contain valid value for bird '" + name + "'.");
					
					/*
					 * Add sighting to model.
					 */
					bird.addSighting(location, date);
				}
			}
		} 
		catch (SAXParseException err) {
			System.err.println("Parsing error" + ", line " + err.getLineNumber() + ", uri " + err.getSystemId());
			System.err.println(" " + err.getMessage());
			return false;
		} 
		catch (Exception e) {
			System.err.println(e.getMessage());
			return false;
		}
		
		return true;
	}
	
	private void saveModel() {
		System.out.println("Saving Model ...");
		
		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

			/*
			 * Create document and root element for birds.
			 */
			Document birdDoc = docBuilder.newDocument();
			Element birdRootElement = birdDoc.createElement(BIRD_ROOT_NODE);
			birdDoc.appendChild(birdRootElement);
			
			/*
			 * Create document and root element for sightings.
			 */
			Document sightingsDoc = docBuilder.newDocument();
			Element sightingsRootElement = sightingsDoc.createElement(SIGHTING_ROOT_NODE);
			sightingsDoc.appendChild(sightingsRootElement);
			
			synchronized (model) {
				for(Bird bird : model.values()) {
					/*
					 * Create bird element.
					 */
					Element birdElement = birdDoc.createElement(BIRD_NODE);
					birdRootElement.appendChild(birdElement);

					/*
					 * Set attributes and their values for birdElement.
					 */
					birdElement.setAttribute(BIRD_NAME_ATTRIBUTE, bird.getName());
					birdElement.setAttribute(BIRD_COLOR_ATTRIBUTE, bird.getColor());
					birdElement.setAttribute(BIRD_WEIGHT_ATTRIBUTE, Float.toString(bird.getWeight()));
					birdElement.setAttribute(BIRD_HEIGHT_ATTRIBUTE,  Float.toString(bird.getHeight()));
					
					/*
					 * Write sightings.
					 */
					BirdSighting[] sightings = bird.getSightings();
					if(sightings.length < 1)
						continue;
					
					Element sightingBirdElemnet = sightingsDoc.createElement(SIGHTING_BIRD_NODE);
					sightingsRootElement.appendChild(sightingBirdElemnet);
					sightingBirdElemnet.setAttribute(SIGHTINGS_NAME_ATTRIBUTE, bird.getName());
					
					for(BirdSighting bs : sightings) {
						/*
						 * Now create sighting element.
						 */
						Element sightingElement = sightingsDoc.createElement(SIGHTING_NODE);
						sightingBirdElemnet.appendChild(sightingElement);
						
						/*
						 * Set attributes.
						 */
						sightingElement.setAttribute(SIGHTINGS_LOCATION_ATTRIBUTE, bs.getLocation());
						if(bs.getDate() != null)
							sightingElement.setAttribute(SIGHTINGS_DATE_ATTRIBUTE, DateFormat.getInstance().format(bs.getDate()));
					}
				}
			}

			/*
			 * Write the content into birds.xml file.
			 */
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.transform(new DOMSource(birdDoc), new StreamResult(birdsFile));
			
			/*
			 * Write the content into sightings.xml file.
			 */
			transformer = transformerFactory.newTransformer();
			transformer.transform(new DOMSource(sightingsDoc), new StreamResult(sightingsFile));
		}
		catch (Exception e) {
			System.err.println(e.getMessage());
		}
		
		System.out.println("Save Model Completed...");
	}
	
	private static boolean validateDateAndTime(String value) {
		if(value == null || value.isEmpty())
			return false;
		
		try {
			/*
			 * Since date formatter rolls date over e.g December 32 becomes
			 * January 1 and December 0 becomes November 30. So checking it as
			 * follows,
			 * 
			 * - Convert String to Date.
			 * - Convert the resultant Date to String.
			 * - Compare the two Strings for equality.
			 */
			Date date = DateFormat.getInstance().parse(value);
			String ps = DateFormat.getInstance().format(date);
			return value.equals(ps);
		}
		catch(Exception e) {
		}
		
		return false;
	}

	/**
	 * Thread which will work on client request.
	 * 
	 * @author muaz
	 *
	 */
	private class WorkerThread extends Thread {
				
		public WorkerThread(String name) {
			super(name);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void run() {
			while (!shutdown || !requests.isEmpty()) {
				Socket socket = null;
				ObjectOutputStream out = null;
				ObjectInputStream in = null;
				
				try {
					/*
					 * If there is no request, the following line will throw
					 * exception which will be handled through catch. Its valid
					 * because otherwise we need to check size by acquiring lock
					 * on requests vector.
					 */
					socket = requests.remove(0);
					out = new ObjectOutputStream(socket.getOutputStream());
					in = new ObjectInputStream(socket.getInputStream());
					
					HashMap<String, Object> request = (HashMap<String, Object>) in.readObject();
					String requestValue = (String) request.get(Constants.REQUEST_TYPE);
					
					System.out.println(getName() + " - Performing request '" + requestValue + "'");
					
					if(Constants.ADD_BIRD_REQUEST.equals(requestValue))
						processAddBirdRequest(request, out);
					else if(Constants.ADD_SIGHTING_REQUEST.equals(requestValue))
						processAddBirdSightingRequest(request, out);
					else if(Constants.LIST_BIRDS_REQUEST.equals(requestValue))
						processListBirdsRequest(out);
					else if(Constants.LIST_SIGHTINGS_REQUEST.equals(requestValue))
						processListBirdsSightingsRequest(request, out);
					else if(Constants.REMOVE_REQUEST.equals(requestValue))
						processRemoveRequest(request, out);
					else if(Constants.QUIT_REQUEST.equals(requestValue))
						processQuitRequest();
				}
				catch (Exception e) {
				}
				finally {
					try {
						/*
						 * Close the client streams and socket.
						 */
						if(out != null)
							out.close();
						
						if(in != null)
							in.close();
						
						if(socket != null)
							socket.close();
					} 
					catch (IOException e) {
					}
				}
				
				try {
					sleep(250);
				} 
				catch (InterruptedException e) {
				}
			}
			
			System.out.println("Shutting down - " + getName());
		}

		private void processAddBirdRequest(HashMap<String, Object> request, ObjectOutputStream out) throws IOException {
			String birdName = (String) request.get(Constants.BIRD_NAME);
			if(birdName == null || birdName.isEmpty()) {
				out.writeObject("Bird name can not be empty.");
				return;
			}
			
			String birdColor = (String) request.get(Constants.BIRD_COLOR);
			float birdWeight = (Float) request.get(Constants.BIRD_WEIGHT);
			float birdHeight = (Float) request.get(Constants.BIRD_HEIGHT);
			
			String message = null;
			synchronized (model) {
				if(model.containsKey(birdName))
					message = "Bird '" + birdName + "' is already present.";
				else
					model.put(birdName, new Bird(birdName, birdColor, birdWeight, birdHeight));
			}
			
			if(message == null)
				message = "Record has been added successfully.";
			
			out.writeObject(message);
		}
		
		private void processAddBirdSightingRequest(HashMap<String, Object> request, ObjectOutputStream out) throws IOException {
			String birdName = (String) request.get(Constants.BIRD_NAME);
			if(birdName == null || birdName.isEmpty()) {
				out.writeObject("Bird name can not be empty.");
				return;
			}
			
			String birdSightingLocation = (String) request.get(Constants.BIRD_SIGHTING_LOCATION);
			Date birdSightingDate = (Date) request.get(Constants.BIRD_SIGHTING_DATE);
			
			String message = null;
			/*
			 * Acquire lock on model because we are performing multiple
			 * operations.
			 */
			synchronized (model) {
				Bird bird = model.get(birdName);
				if(bird == null)
					message = "Bird '" + birdName + "' is not present.";
				else
					bird.addSighting(birdSightingLocation, birdSightingDate);
			}
			
			if(message == null)
				message = "Record has been added successfully.";
			
			out.writeObject(message);
		}
		
		private void processListBirdsRequest(ObjectOutputStream out) throws IOException {
			Bird[] birds = new Bird[0];
			synchronized (model) {
				birds = model.values().toArray(new Bird[0]);
			}
			
			if(birds == null)
				birds = new Bird[0];
			
			out.writeObject(birds);
		}
		
		private void processListBirdsSightingsRequest(HashMap<String,Object> request, ObjectOutputStream out) throws IOException {
			String birdNameRegex = (String) request.get(Constants.BIRD_NAME);
			if(birdNameRegex == null || birdNameRegex.isEmpty()) {
				out.writeObject(new BirdSighting[0]);
				return;
			}
			
			Date startDate = (Date) request.get(Constants.START_DATE);
			Date endDate = (Date) request.get(Constants.END_DATE);
			
			ArrayList<BirdSighting> sightings = new ArrayList<BirdSighting>();
			synchronized (model) {
				/*
				 * Get the sightings matching the bird name regular expression.
				 */
				for(String name : model.keySet().toArray(new String[0])) {
					if(name.matches(birdNameRegex)) {
						Bird bird = model.get(name);
						sightings.addAll(Arrays.asList(bird.getSightings()));
					}
				}
				
				/*
				 * Now filter on the bases of date range.
				 */
				for(BirdSighting bs : sightings.toArray(new BirdSighting[0])) {
					Date date = bs.getDate();
					if(date != null && startDate.before(date) && endDate.after(date))
						continue;
					
					sightings.remove(bs);
				}
			}
			
			out.writeObject(sightings.toArray(new BirdSighting[0]));
		}

		private void processRemoveRequest(HashMap<String, Object> request, ObjectOutputStream out) throws IOException {
			String birdName = (String) request.get(Constants.BIRD_NAME);
			if(birdName == null || birdName.isEmpty()) {
				out.writeObject("Bird name can not be empty.");
				return;
			}
			
			/*
			 * Don't need to syncronize because hash table is thread safe. 
			 */
			if(model.remove(birdName) == null)
				out.writeObject("Unable to remove. " + birdName + " is not present.");
			else
				out.writeObject("Successfully remove bird '" + birdName + "'");
		}

		private void processQuitRequest() throws IOException {
			shutdown = true;
			
			if(!serverSocket.isClosed())
				serverSocket.close();
		}
	}
	
	/**
	 * Thread which will work on client request.
	 * 
	 * @author muaz
	 *
	 */
	private class SaveModelThread extends Thread {
		
		public SaveModelThread() {
			super("Save Model Thread");
		}

		@Override
		public void run() {
			while (!shutdown) {
				try {
					sleep(SAVE_MODEL_INTERVAL);
				} 
				catch (InterruptedException e) {
					System.out.println("Save Model Thread has interuppted.");
				}
				
				/*
				 * check again after sleep that whether server is
				 * shutting down or not.
				 */
				if(shutdown)
					break;
				
				/*
				 * Save model to xml files. Following functions gets lock on model
				 * when needed so no need to acquire lock here. 
				 */
				saveModel();
			}
			
			System.out.println("Shutting down - " + getName());
		}
	}
	
	/*
	 * 
	 * Main method.
	 * 
	 */
	public static void main(String[] args) {
		/*
		 * Make application headless.
		 */
        System.setProperty("java.awt.headless", "true");
        
		/*
		 * All switches must have values. For default values, switches must be
		 * missing.
		 */
        if(args.length % 2 != 0) {
        	System.out.println("All options must contain values or should be skip to use default value.");
        	System.exit(-1);
        }
        
        /*
         * Read command line argument.
         */
        int port = Constants.DEFAULT_PORT;
        String dataLocation = null;
        int procCount = DEFAULT_PROC_COUNT;
        
        for(int i = 0; i < args.length; i++) {
        	String s = args[i];
        	String v = args[++i];
        	
        	if(PORT_SWITCH.equals(s)) {
        		try {
        			port = Integer.parseInt(v);
        			if(port < Constants.MIN_PORT || port > Constants.MAX_PORT) {
        				System.err.println("'" + PORT_SWITCH + "' should be between " + Constants.MIN_PORT + " & " + Constants.MAX_PORT + " range, using default.");
        				port = Constants.DEFAULT_PORT;
        			}
        		}
        		catch(NumberFormatException e) {
        			System.err.println("'" + PORT_SWITCH + "' does not has a valid input, using default.");
        		}
        	}
        	else if(DATA_SWITCH.equals(s)) {
        		File file = new File(v);
        		
				/*
				 * If the given path does not exit, it's a valid input
				 * because we could create folder at the given location.
				 */
				if(!file.exists()) {	
					dataLocation = v;
					continue;
				}
				
				/*
				 * If path exists and it is a file so use default value.
				 */
				if(file.isFile()) {
					System.err.println("'" + DATA_SWITCH + "' has a file path. using default.");
					continue;
				}
				
				dataLocation = v;
        	}
        	else if(PROC_COUNT_SWITCH.equals(s)) {
        		try {
        			procCount = Integer.parseInt(v);
        			if(procCount < 1) {
        				System.err.println("'" + PROC_COUNT_SWITCH + "' should be a positive integer, using default.");
        				port = DEFAULT_PROC_COUNT;
        			}
        		}
        		catch(NumberFormatException e) {
        			System.err.println("'" + PROC_COUNT_SWITCH + ", does not has a valid input, using default.");
        		}
        	}
        	else {
        		System.err.println("Not a valid option, ignoring...");
        	}
        }
        
        /*
         * If dataLocation is null, find user directory path.
         */
        if(dataLocation == null)
        	dataLocation = System.getProperty("user.home");
        
		/*
		 * If we are unable to locate user directory or user does not provide
		 * any directory, exit gracefully.
		 */
        if(dataLocation == null) {
        	System.err.println("Could not create serverdata folder, exiting.");
        	System.exit(-1);
        }
        
        if(!dataLocation.endsWith(DATA_FOLDER))
        	dataLocation += "\\" + DATA_FOLDER;
        
        File serverDataFolder = new File(dataLocation);
        
		/*
		 * If server data folder does not exist, try to create it.
		 */
        if(!serverDataFolder.exists())
        	serverDataFolder.mkdir();
        
        /*
         *  If server data folder still does not exist, exit gracefully.
         */
        if(!serverDataFolder.exists()) {
        	System.err.println("Could not create serverdata folder, exiting.");
        	System.exit(-1);
        }
        
        /*
         * Set permissions.
         */
        serverDataFolder.setReadable(true);
        serverDataFolder.setWritable(true);
        if(!serverDataFolder.canRead() || !serverDataFolder.canWrite()) {
        	System.err.println("Does not has permissions on " + serverDataFolder.getAbsolutePath() + ", exiting.");
        	System.exit(-1);
        }
        
        /*
         * Print values.
         */
        System.out.println("Server is going to start with following values,");
        System.out.println(PORT_SWITCH + " = " + port);
        System.out.println(DATA_SWITCH + " = " + serverDataFolder.getAbsolutePath());
        System.out.println(PROC_COUNT_SWITCH + " = " + procCount);
        
        /*
         * Run server now.
         */
        new Server(port, serverDataFolder, procCount).run();
	}
}
