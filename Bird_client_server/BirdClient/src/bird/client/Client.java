package bird.client;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import bird.model.Bird;
import bird.model.BirdSighting;
import bird.model.Constants;



public class Client {
	private static final String SERVER_PORT_SWITCH = "-serverPort";
	
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
         * We could maximum have three arguments.
         */
        if(args.length > 3) {
        	System.out.println("Too many command line arguments, exiting.");
        	System.exit(-1);
        }
        
        /*
         * Read command line argument.
         */
        int port = Constants.DEFAULT_PORT;
        String requestType = null;
        
        for(int i = 0; i < args.length; i++) {
        	String s = args[i];
        	
        	if(SERVER_PORT_SWITCH.equals(s)) {
        		try {
        			String v = args[i + 1];
        			port = Integer.parseInt(v);
        			/*
        			 * Now increment the index because we are sure that we got value.
        			 */
        			i++;
        			
        			if(port < Constants.MIN_PORT || port >Constants. MAX_PORT) {
        				System.err.println(SERVER_PORT_SWITCH + " should be between " + Constants.MIN_PORT + " & " + Constants.MAX_PORT + " range, using default.");
        				port = Constants.DEFAULT_PORT;
        			}
        		}
        		catch(NumberFormatException e) {
        			System.err.println(SERVER_PORT_SWITCH + " does not has a valid input, using default.");
        		}
        		catch(ArrayIndexOutOfBoundsException e1) {
        			System.err.println(SERVER_PORT_SWITCH + " does not has a value, using default.");
        		}
        	}
        	else if(Constants.ADD_BIRD_REQUEST.equals(s)) {
        		requestType = Constants.ADD_BIRD_REQUEST;
        	}
        	else if(Constants.ADD_SIGHTING_REQUEST.equals(s)) {
        		requestType = Constants.ADD_SIGHTING_REQUEST;
        	}
        	else if(Constants.LIST_BIRDS_REQUEST.equals(s)) {
        		requestType = Constants.LIST_BIRDS_REQUEST;
        	}
        	else if(Constants.LIST_SIGHTINGS_REQUEST.equals(s)) {
        		requestType = Constants.LIST_SIGHTINGS_REQUEST;
        	}
        	else if(Constants.REMOVE_REQUEST.equals(s)) {
        		requestType = Constants.REMOVE_REQUEST;
        	}
        	else if(Constants.QUIT_REQUEST.equals(s)) {
        		requestType = Constants.QUIT_REQUEST;
        	}
        	else {
        		System.err.println("Not a valid option, ignoring...");
        	}
        }
        
        if(requestType == null) {
        	System.err.println("Could not find a valid option, exiting...");
        	System.exit(-1);
        }
        
        Socket socket = null;
        ObjectInputStream in = null;
        ObjectOutputStream out = null;
        
        try {
			socket = new Socket("localhost", port);
			out = new ObjectOutputStream(socket.getOutputStream());
			in = new ObjectInputStream(socket.getInputStream());
		} 
        catch (Exception e) {
        	System.err.println("Unable to connect to server, exiting...");
        	System.exit(-1);
		}
        
        HashMap<String, Object> request = new HashMap<String, Object>();
        request.put(Constants.REQUEST_TYPE, requestType);
        
    	try {
    		if(Constants.ADD_BIRD_REQUEST.equals(requestType)) {
    			sendAddBirdRequest(request, in, out);
        	}
        	else if(Constants.ADD_SIGHTING_REQUEST.equals(requestType)) {
        		sendAddSightingRequest(request, in, out);
        	}
        	else if(Constants.LIST_BIRDS_REQUEST.equals(requestType)) {
        		processListBirdsRequest(request, in, out);
        	}
        	else if(Constants.LIST_SIGHTINGS_REQUEST.equals(requestType)) {
        		processListBirdSightingsRequest(request, in, out);
        	}
        	else if(Constants.REMOVE_REQUEST.equals(requestType)) {
        		sendRemoveBirdRequest(request, in, out);
        	}
        	else if(Constants.QUIT_REQUEST.equals(requestType)) {
        		/*
        		 * Send request.
        		 */
    			out.writeObject(request);
    		}
		} 
    	catch (Exception e) {
    		System.err.println("An error occured while transferring/receiving data from server, exiting...");
		}
    	finally {
    		try {
				in.close();
				out.close();
				socket.close();
			} 
        	catch (IOException e) {
			}
    	}
	}

	private static void sendAddBirdRequest(HashMap<String, Object> request, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
		Scanner scanner = new Scanner(System.in);
		
		try {
			/*
			 * Get bird name.
			 */
			System.out.print("Enter bird name: ");
			String birdName = scanner.nextLine();
			if(birdName.isEmpty()) {
				System.err.println("Bird name can not be empty, exiting.");
				return;
			}
			
			request.put(Constants.BIRD_NAME, birdName);
			
			/*
			 * Get bird color.
			 */
			System.out.print("Enter bird color: ");
			String birdColor = scanner.nextLine();
			request.put(Constants.BIRD_COLOR, birdColor);
			
			try {
				/*
				 * Get bird weight.
				 */
				System.out.print("Enter bird weight: ");
				float birdWeight = scanner.nextFloat();
				request.put(Constants.BIRD_WEIGHT, birdWeight);
				
				/*
				 * Get bird height.
				 */
				System.out.print("Enter bird weight: ");
				float birdHeight = scanner.nextFloat();
				request.put(Constants.BIRD_HEIGHT, birdHeight);
			}
			catch(InputMismatchException e) {
				System.err.println("Invalid input, exiting.");
				return;
			}
			
			/*
			 * Send request.
			 */
			out.writeObject(request);
			
			/*
			 * Wait for response.
			 */
			String result = (String) in.readObject();
			System.out.println(result);
		}
		finally {
			scanner.close();
		}
	}
	
	private static void sendAddSightingRequest(HashMap<String, Object> request, ObjectInputStream in, ObjectOutputStream out)  throws IOException, ClassNotFoundException {
		Scanner scanner = new Scanner(System.in);
		
		try {
			/*
			 * Get bird name.
			 */
			System.out.print("Enter bird name: ");
			String birdName = scanner.nextLine();
			if(birdName.isEmpty()) {
				System.err.println("Bird name can not be empty, exiting.");
				return;
			}
			
			request.put(Constants.BIRD_NAME, birdName);
			
			/*
			 * Get bird color.
			 */
			System.out.print("Enter sighting location: ");
			String birdSightingLocation = scanner.nextLine();
			request.put(Constants.BIRD_SIGHTING_LOCATION, birdSightingLocation);
			
			try {
				/*
				 * Get bird weight.
				 */
				System.out.print("Enter sighting date (DD/MM/YY HH:MM): ");
				String birdSightingDate = scanner.nextLine();
				if(!validateDateAndTime(birdSightingDate))
					throw new ParseException("", 0);
					
				request.put(Constants.BIRD_SIGHTING_DATE, DateFormat.getInstance().parse(birdSightingDate));
			}
			catch(ParseException e) {
				System.err.println("Invalid input, exiting.");
				return;
			}
			
			/*
			 * Send request.
			 */
			out.writeObject(request);
			
			/*
			 * Wait for response.
			 */
			String result = (String) in.readObject();
			System.out.println(result);
		}
		finally {
			scanner.close();
		}
	}

	private static void sendRemoveBirdRequest(HashMap<String, Object> request, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
		Scanner scanner = new Scanner(System.in);
		
		try {
			System.out.print("Enter bird name to remove (or just press enter to quit): ");
			String birdName = scanner.nextLine();
			if(birdName.isEmpty())
				return;
			
			request.put(Constants.BIRD_NAME, birdName);
			/*
			 * Send request.
			 */
			out.writeObject(request);
			
			/*
			 * Wait for response.
			 */
			String result = (String) in.readObject();
			System.out.println(result);
		}
		finally {
			scanner.close();
		}
	}
	
	private static void processListBirdsRequest(HashMap<String, Object> request, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
		/*
		 * Send request.
		 */
		out.writeObject(request);
		
		/*
		 * Wait for response.
		 */
		Bird[] result = (Bird[]) in.readObject();
		if(result.length < 1) {
			System.out.println("No record to show");
			return;
		}
		
		String format = "%-30.30s %-20.20s %-15.15s %-15.15s\n";
		System.out.printf(format, "Name", "Color", "Weight", "Height");
		for(Bird bird : result)
			System.out.printf(format, bird.getName(), bird.getColor(), "" + bird.getWeight(), "" + bird.getHeight());
		
		System.out.println("\n");
		System.out.println("Total number of records: " + result.length);
	}
	
	private static void processListBirdSightingsRequest(HashMap<String, Object> request, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
		Scanner scanner = new Scanner(System.in);
		
		try {
			/*
			 * Get bird name.
			 */
			System.out.print("Enter bird name (can be a regular expression): ");
			String birdName = scanner.nextLine();
			if(birdName.isEmpty()) {
				System.err.println("Bird name can not be empty, exiting.");
				return;
			}
			
			try {
				Pattern.compile(birdName);
			}
			catch(PatternSyntaxException e) {
				System.err.println("Invalid regular expression for Bird name, exiting.");
				return;
			}
			
			request.put(Constants.BIRD_NAME, birdName);
			
			try {
				/*
				 * Get start date.
				 */
				System.out.print("Enter exclusive start date (DD/MM/YY HH:MM): ");
				String startDate = scanner.nextLine();
				if(!validateDateAndTime(startDate))
					throw new ParseException("", 0);
				
				request.put(Constants.START_DATE, DateFormat.getInstance().parse(startDate));
				
				/*
				 * Get end date.
				 */
				System.out.print("Enter exclusive end date (DD/MM/YY HH:MM): ");
				String endDate = scanner.nextLine();
				if(!validateDateAndTime(endDate))
					throw new ParseException("", 0);
				
				request.put(Constants.END_DATE, DateFormat.getInstance().parse(endDate));
			}
			catch(ParseException e) {
				System.err.println("Invalid input, exiting.");
				return;
			}
			
			/*
			 * Send request.
			 */
			out.writeObject(request);
			
			/*
			 * Wait for response.
			 */
			BirdSighting[] result = (BirdSighting[]) in.readObject();
			if(result.length < 1) {
				System.out.println("\nNo record to show");
				return;
			}
			
			/*
			 * Sort result on following rules,
			 * - Sort on name first.
			 * - Sort on date second.
			 */
			Arrays.sort(result, new Comparator<BirdSighting>() {
				@Override
				public int compare(BirdSighting o1, BirdSighting o2) {
					String n1 = o1.getName().toLowerCase();
					String n2 = o2.getName().toLowerCase();
					int result = n2.compareTo(n1);
					if(result != 0)
						return result;
					
					/*
					 * If both names are equal sort on date.
					 */
					Date d1 = o1.getDate();
					Date d2 = o2.getDate();
					if(d1 == null && d2 == null)
						return 0;
					
					if(d2 == null)
						return -1;
					
					return d2.compareTo(d1);
				}
			});
			
			String format = "%-30.30s %-40.40s\n";
			System.out.printf(format, "Name", "Date");
			for(BirdSighting birdSighting : result) {
				String date = "";
				try {
					date = DateFormat.getInstance().format(birdSighting.getDate());
				}
				catch(Exception e) {
				}
				
				System.out.printf(format, birdSighting.getName(), date);
			}
			
			System.out.println("\n");
			System.out.println("Total number of records: " + result.length);
		}
		finally {
			scanner.close();
		}
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
}
