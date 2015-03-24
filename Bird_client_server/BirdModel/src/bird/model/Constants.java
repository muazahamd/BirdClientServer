package bird.model;

public interface Constants {
	public static final String REQUEST_TYPE = "request_type";
	
	public static final String ADD_BIRD_REQUEST = "-addbird";
	public static final String ADD_SIGHTING_REQUEST = "-addsighting";
	public static final String LIST_BIRDS_REQUEST = "-listbirds";
	public static final String LIST_SIGHTINGS_REQUEST = "-listsightings";
	public static final String REMOVE_REQUEST = "-remove";
	public static final String QUIT_REQUEST = "-quit";
	
	public static final String BIRD_NAME = "bird_name";
	public static final String BIRD_COLOR = "bird_color";
	public static final String BIRD_WEIGHT = "bird_weight";
	public static final String BIRD_HEIGHT = "bird_height";
	
	public static final String BIRD_SIGHTING_LOCATION = "bird_sighting_location";
	public static final String BIRD_SIGHTING_DATE = "bird_sighting_date";
	
	public static final String START_DATE = "start_date";
	public static final String END_DATE = "end_date";
	
	public static final int DEFAULT_PORT = 3000;
	public static final int MIN_PORT = 1;
	public static final int MAX_PORT = 65535;
}
