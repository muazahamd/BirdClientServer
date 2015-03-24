package bird.model;
import java.io.Serializable;
import java.util.Date;
import java.util.Vector;

/**
 * Model class to hold a bird information.
 * 
 * @author muaz
 *
 */
public class Bird implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -803892748406877600L;
	
	private String name;
	private String color;
	private float weight;
	private float height;
	private Vector<BirdSighting> sightings = new Vector<BirdSighting>();
	
	public Bird(String name, String color, float weight, float height) {
		this.name = name;
		this.color = color == null ? "" : color;
		this.weight = weight;
		this.height = height;
	}

	public String getName() {
		return name;
	}

	public String getColor() {
		return color;
	}

	public float getWeight() {
		return weight;
	}

	public float getHeight() {
		return height;
	}

	public void addSighting(String location, Date date) {
		sightings.add(new BirdSighting(name, location, date));
	}
	
	public BirdSighting[] getSightings() {
		return sightings.toArray(new BirdSighting[0]);
	}
}
