package de.fernunihagen.dna.jkn.scalephant.storage.sstable;

import java.nio.ByteBuffer;

import de.fernunihagen.dna.jkn.scalephant.storage.StorageManagerException;

public class SSTableHelper {
	
	/**
	 * Size of a float in bytes
	 */
	public final static int FLOAT_BYTES = Float.SIZE / Byte.SIZE;
	
	/**
	 * Size of a long in bytes
	 */
	public final static int LONG_BYTES = Long.SIZE / Byte.SIZE;
	
	/**
	 * Size of a integer in bytes
	 */
	public final static int INT_BYTES = Integer.SIZE / Byte.SIZE;
	
	/**
	 * Size of a short in bytes
	 */
	public final static int SHORT_BYTES = Short.SIZE / Byte.SIZE;
	
	
	/** 
	 * Convert a array of long values into a byte buffer
	 * @param longValues
	 * @return
	 */
	public static ByteBuffer longArrayToByteBuffer(long longValues[]) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(LONG_BYTES * longValues.length);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		
		for(int i = 0; i < longValues.length; i++) {
			byteBuffer.putLong(longValues[i]);
		}
		
		return byteBuffer;
	}
	
	/** 
	 * Convert a array of float values into a byte buffer
	 * @param longValues
	 * @return
	 */
	public static ByteBuffer floatArrayToByteBuffer(float floatValues[]) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(FLOAT_BYTES * floatValues.length);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		
		for(int i = 0; i < floatValues.length; i++) {
			byteBuffer.putFloat(floatValues[i]);
		}
		
		return byteBuffer;
	}
	
	/**
	 * Encode a long into a byte buffer
	 * 
	 * @param longValue
	 * @return the long value
	 */
	public static ByteBuffer longToByteBuffer(long longValue) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(LONG_BYTES);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		byteBuffer.putLong(longValue);
		return byteBuffer;
	}
	
	/**
	 * Encode a float into a byte buffer
	 * 
	 * @param floatValue
	 * @return the long value
	 */
	public static ByteBuffer floatToByteBuffer(float floatValue) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(FLOAT_BYTES);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		byteBuffer.putFloat(floatValue);
		return byteBuffer;
	}
	
	/**
	 * Encode an integer into a byte buffer
	 * 
	 * @param intValue
	 * @return the int value
	 */
	public static ByteBuffer intToByteBuffer(int intValue) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(INT_BYTES);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		byteBuffer.putInt(intValue);
		return byteBuffer;		
	}
	
	/**
	 * Encode a short into a byte buffer
	 * @param shortValue
	 * @return the short value
	 */
	public static ByteBuffer shortToByteBuffer(short shortValue) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(SHORT_BYTES);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		byteBuffer.putShort(shortValue);
		return byteBuffer;		
	}
	
	/**
	 * Decode a long array from a byte buffer
	 * @param buffer
	 * @return the long value
	 */
	public static long[] readLongArrayFromByte(byte[] buffer) {
		final int totalValues = buffer.length / LONG_BYTES;
		long values[] = new long[totalValues];
		final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		
		for(int i = 0; i < totalValues; i++) {
			values[i] = byteBuffer.getLong(i * LONG_BYTES);
		}
		
		return values;
	}
	
	/**
	 * Decode a long from a byte buffer
	 * @param buffer
	 * @return the long value
	 */
	public static long readLongFromByte(byte[] buffer) {
		final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		return byteBuffer.getLong();
	}
	
	/**
	 * Decode an int from a byte buffer
	 * @param buffer
	 * @return the int value
	 */
	public static int readIntFromByte(byte[] buffer) {
		final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		return byteBuffer.getInt();
	}
	
	/**
	 * Decode a short from a byte buffer
	 * @param buffer
	 * @return the short value
	 */
	public static short readShortFromByte(byte[] buffer) {
		final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		return byteBuffer.getShort();
	}
	
	/**
	 * Extract the sequence Number from a given filename
	 * 
	 * @param Tablename, the name of the Table. Filename, the name of the file
	 * @return the sequence number
	 * @throws StorageManagerException 
	 */
	public static int extractSequenceFromFilename(final String tablename, final String filename)
			throws StorageManagerException {
		try {
			final String sequence = filename
				.replace(SSTableConst.SST_FILE_PREFIX + tablename + "_", "")
				.replace(SSTableConst.SST_FILE_SUFFIX, "")
				.replace(SSTableConst.SST_INDEX_SUFFIX, "");
			
			return Integer.parseInt(sequence);
		
		} catch (NumberFormatException e) {
			String error = "Unable to parse sequence number: " + filename;
			throw new StorageManagerException(error, e);
		}
	}

}
