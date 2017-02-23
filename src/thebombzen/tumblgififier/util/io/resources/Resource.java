package thebombzen.tumblgififier.util.io.resources;

public class Resource {
	
	/**
	 * This is the global package containing the resource.
	 * Example: "FFmpeg"
	 */
	public final String resourcePackage;
	/**
	 * This is the local name of the resource.
	 * Example: "ffplay"
	 */
	public final String resourceName;
	
	/**
	 * This is the filesystem location of the resource.
	 * Example: "/usr/bin/ffprobe"
	 */
	public final String location;
	
	/**
	 * Did this resource originate from the PATH?
	 */
	public final boolean isInPath;
	
	public Resource(String resourcePackage, String resourceName, String location, boolean isFromPATH) {
		this.resourcePackage = resourcePackage;
		this.resourceName = resourceName;
		this.location = location;
		this.isInPath = isFromPATH;
	}
	
	/**
	 * Calling toString() on a resource just returns its location.
	 */
	@Override
	public String toString() {
		return location;
	}
}