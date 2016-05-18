package de.fernunihagen.dna.jkn.scalephant.storage.entity;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fernunihagen.dna.jkn.scalephant.storage.sstable.SSTableHelper;

public class BoundingBox implements Comparable<BoundingBox> {
	
	public final static BoundingBox EMPTY_BOX = new BoundingBox();
	
	/**
	 * The boundingBox contains a interval for each dimension
	 */
	protected final List<FloatInterval> boundingBox;
	
	/**
	 * The return value of an invalid dimension
	 */
	public final static int INVALID_DIMENSION = -1;
	
	/**
	 * The Logger
	 */
	protected static final Logger logger = LoggerFactory.getLogger(BoundingBox.class);
	
	public BoundingBox(final Float... args) {
		
		if(args.length % 2 != 0) {
			throw new IllegalArgumentException("Even number of arguments expected");
		}
		
		boundingBox = new ArrayList<FloatInterval>(args.length / 2);
				
		for(int i = 0; i < args.length; i = i + 2) {
			final FloatInterval interval = new FloatInterval(args[i], args[i] + args[i+1]);
			boundingBox.add(interval);
		}				
	}
	
	public BoundingBox(float[] values) {
		
		if(values.length % 2 != 0) {
			throw new IllegalArgumentException("Even number of arguments expected");
		}
		
		boundingBox = new ArrayList<FloatInterval>(values.length / 2);
		
		for(int i = 0; i < values.length; i = i + 2) {
			final FloatInterval interval = new FloatInterval(values[i], values[i] + values[i+1]);
			boundingBox.add(interval);
		}				
	}

	/**
	 * Returns the size of the bounding box in bytes
	 * 
	 * @return
	 */
	public int getSize() {
		return boundingBox.size();
	}
	
	/**
	 * Convert the bounding box into a byte array
	 * 
	 * @return
	 */
	public byte[] toByteArray() {
		final float[] values = toFloatArray();
		
		return SSTableHelper.floatArrayToIEEE754ByteBuffer(values).array();
	}

	/**
	 * Convert the boudning box into a float array
	 * 
	 * @return
	 */
	public float[] toFloatArray() {
		final float[] values = new float[boundingBox.size() * 2];
		for(int i = 0; i < boundingBox.size(); i = i + 2) {
			values[i] = boundingBox.get(i).getBegin();
			values[i+1] = boundingBox.get(i).getEnd();
		}
		return values;
	}
	
	/**
	 * Read the bounding box from a byte array
	 * @param boxBytes
	 * @return
	 */
	public static BoundingBox fromByteArray(final byte[] boxBytes) {
		final float[] floatArray = SSTableHelper.readIEEE754FloatArrayFromByte(boxBytes);
		return new BoundingBox(floatArray);
	}
	
	/**
	 * Tests if two bounding boxes share some space
	 * @param otherBoundingBox
	 * @return
	 */
	public boolean overlaps(final BoundingBox otherBoundingBox) {
		
		// Null does overlap with nothing
		if(otherBoundingBox == null) {
			return false;
		}
		
		// The empty bounding box overlaps everything
		if(otherBoundingBox == BoundingBox.EMPTY_BOX) {
			return true;
		}
		
		// Both boxes are equal (Case 5)
		if(equals(otherBoundingBox)) {
			return true;
		}
		
		// Dimensions are not equal
		if(otherBoundingBox.getDimension() != getDimension()) {
			return false;
		}
		
		// Check the overlapping in each dimension d
		for(int d = 0; d < getDimension(); d++) {
			
			final FloatInterval ourInterval = boundingBox.get(d);
			final FloatInterval otherInterval = otherBoundingBox.getIntervalForDimension(d);
			
			
			if(! ourInterval.isOverlappingWith(otherInterval)) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Does the bounding box covers the point in the dimension?
	 * @param point
	 * @param dimension
	 * @return
	 */
	public boolean isCoveringPointInDimension(float point, int dimension) {
		if(getCoordinateLow(dimension) <= point && getCoordinateHigh(dimension) >= point) {
			return true;
		}
		
		return false;
	}
	
	/**
	 * Get the extent for the dimension
	 * @param dimension
	 * @return
	 */
	public float getExtent(final int dimension) {
		return boundingBox.get(dimension).getEnd() - boundingBox.get(dimension).getBegin();
	}
	
	/**
	 * Get the float interval for the given dimension
	 * @param dimension
	 * @return
	 */
	public FloatInterval getIntervalForDimension(final int dimension) {
		return boundingBox.get(dimension);
	}
	
	/**
	 * The the lowest coordinate for the dimension
	 * @param dimension
	 * @return
	 */
	public float getCoordinateLow(final int dimension) {
		return boundingBox.get(dimension).getBegin();
	}
	
	/**
	 * The the highest coordinate for the dimension
	 * @param dimension
	 * @return
	 */
	public float getCoordinateHigh(final int dimension) {
		return boundingBox.get(dimension).getEnd();
	}
	
	/**
	 * Return the dimension of the bounding box
	 * @return
	 */
	public int getDimension() {
		return boundingBox.size();
	}

	/**
	 * Convert to a readable string
	 * 
	 */
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("BoundingBox [dimensions=");
		sb.append(getDimension());
		
		for(int d = 0; d < getDimension(); d++) {
			sb.append(", dimension ");
			sb.append(d);
			sb.append(" low: ");
			sb.append(getCoordinateLow(d));
			sb.append(" high: ");
			sb.append(getCoordinateHigh(d));
		}
				
		sb.append("]");
		return sb.toString();
	}

	/**
	 * Convert into a hashcode
	 * 
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((boundingBox == null) ? 0 : boundingBox.hashCode());
		return result;
	}

	/**
	 * Is equals with an other object
	 * 
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BoundingBox other = (BoundingBox) obj;
		if (boundingBox == null) {
			if (other.boundingBox != null)
				return false;
		} else if (!boundingBox.equals(other.boundingBox))
			return false;
		return true;
	}

	/**
	 * Compare to an other boudning box
	 */
	@Override
	public int compareTo(final BoundingBox otherBox) {
		
		// Check number od dimensions
		if(getDimension() != otherBox.getDimension()) {
			return getDimension() - otherBox.getDimension(); 
		}
		
		// Check start point of each dimension
		for(int d = 0; d < getDimension(); d++) {
			if(getCoordinateLow(d) != otherBox.getCoordinateLow(d)) {
				if(getCoordinateLow(d) > otherBox.getCoordinateLow(d)) {
					return 1;
				} else {
					return -1;
				}
			}
		}
		
		// Objects are equal
		return 0;
	}
	
	/**
	 * Get the bounding box of two bounding boxes
	 * @param boundingBox1
	 * @param boundingBox2
	 * @return
	 */
	public static BoundingBox getBoundingBox(final BoundingBox... boundingBoxes) {
		
		// No argument
		if(boundingBoxes.length == 0) {
			return null;
		}
		
		// Only 1 argument
		if(boundingBoxes.length == 1) {
			return boundingBoxes[0];
		}
		
		int dimensions = boundingBoxes[0].getDimension();
		
		// All bounding boxes need the same dimension
		for(int i = 1 ; i < boundingBoxes.length; i++) {
			
			final BoundingBox curentBox = boundingBoxes[i];
			
			// Bounding box could be null, e.g. for DeletedTuple instances
			if(curentBox == null) {
				continue;
			}
			
			if(dimensions != curentBox.getDimension()) {
				logger.warn("Merging bounding boxed with different dimensions");
				return null;
			}
		}
		
		// Array with data for the result box
		final float[] coverBox = new float[boundingBoxes[0].getDimension() * 2];
		
		// Construct the covering bounding box
		for(int d = 0; d < dimensions; d++) {
			float resultMin = Float.MAX_VALUE;
			float resultMax = Float.MIN_VALUE;
			
			for(int i = 0; i < boundingBoxes.length; i++) {
				resultMin = Math.min(resultMin, boundingBoxes[i].getCoordinateLow(d));
				resultMax = Math.max(resultMax, boundingBoxes[i].getCoordinateHigh(d));
			}
			
			coverBox[2 * d] = resultMin; // Start position
			coverBox[2 * d + 1] = resultMax - resultMin; // Extend
		}
		
		return new BoundingBox(coverBox);
	}
	
}
