package bird.model;
import java.io.Serializable;
import java.util.Date;

/**
 * Model class to hold a sighting information.
 * 
 * @author muaz
 *
 */
public class BirdSighting implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4971523045093654047L;
	
	private String name;
	private String location;
	private Date date;
	
	public BirdSighting(String name, String location, Date date) {
		this.name = name;
		this.location = location == null ? "" : location;
		this.date = date;
	}

	public String getName() {
		return name;
	}
	
	public String getLocation() {
		return location;
	}

	public Date getDate() {
		return date;
	}
}
