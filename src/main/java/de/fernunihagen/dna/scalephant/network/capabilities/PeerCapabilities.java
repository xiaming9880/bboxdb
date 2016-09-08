package de.fernunihagen.dna.scalephant.network.capabilities;

import java.nio.ByteBuffer;

public class PeerCapabilities {

	protected final ByteBuffer capabilities;
	
	/**
	 * The compression flag
	 */
	public final static short CAPABILITY_COMPRESSION = 0;
	
	/**
	 * The length of the capabilities
	 */
	public final static short CAPABILITY_BYTES = 4;
	
	public PeerCapabilities() {
		capabilities = ByteBuffer.allocate(CAPABILITY_BYTES);
		capabilities.clear();		
	}
	
	public PeerCapabilities(final byte[] byteArray) {
		if(byteArray.length != CAPABILITY_BYTES) {
			throw new IllegalArgumentException("Wrong lenght of the byteArray: " + byteArray.length);
		}
		
		capabilities = ByteBuffer.wrap(byteArray);
	}
	
	/**
	 * Is the compression bit set?
	 * @return
	 */
	public boolean hasCompression() {
		return getBit(CAPABILITY_COMPRESSION);
	}

	/**
	 * Set the compression bit
	 */
	public void setCompression() {
		setBit(CAPABILITY_COMPRESSION);
	}
	
	/**
	 * Clear the compression bit
	 */
	public void clearCompression() {
		clearBit(CAPABILITY_COMPRESSION);
	}
	
	/**
	 * Set the bit
	 * @param bit
	 */
	protected void setBit(final int bit) {
		 final int byteNumber = bit / 8;
		 final int offset = bit % 8;
		 byte b = capabilities.get(byteNumber);
	     b |= 1 << offset;
	     capabilities.put(byteNumber, b);
	}
	
	/**
	 * Clear the bit
	 * @param bit
	 */
	protected void clearBit(final int bit) {
		 final int byteNumber = bit / 8;
		 final int offset = bit % 8;
		 byte b = capabilities.get(byteNumber);
	     b &= ~(1 << offset);
	     capabilities.put(byteNumber, b);
	}
	
	/**
	 * Is the bit set?
	 * @param bit
	 * @return 
	 */
	protected boolean getBit(final int bit) {
		 final int byteNumber = bit / 8;
		 final int offset = bit % 8;
		 byte b = capabilities.get(byteNumber);
		 b &= 1 << offset;
		 
		 return b != 0;
	}
	
	/**
	 * Convert the capabilities into a byte array
	 * @return
	 */
	public byte[] toByteArray() {
		return capabilities.array();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((capabilities == null) ? 0 : capabilities.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PeerCapabilities other = (PeerCapabilities) obj;
		if (capabilities == null) {
			if (other.capabilities != null)
				return false;
		} else if (!capabilities.equals(other.capabilities))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "PeerCapabilities [capabilities=" + capabilities + "]";
	}
	
}
