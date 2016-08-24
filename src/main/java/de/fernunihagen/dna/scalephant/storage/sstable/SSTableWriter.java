package de.fernunihagen.dna.scalephant.storage.sstable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fernunihagen.dna.scalephant.storage.StorageManagerException;
import de.fernunihagen.dna.scalephant.storage.entity.SSTableName;
import de.fernunihagen.dna.scalephant.storage.entity.SStableMetaData;
import de.fernunihagen.dna.scalephant.storage.entity.Tuple;
import de.fernunihagen.dna.scalephant.tools.DataEncoderHelper;

public class SSTableWriter implements AutoCloseable {
	
	/**
	 * The number of the table
	 */
	protected final int tablebumber;
	
	/**
	 * The name of the table
	 */
	protected final SSTableName name;
	
	/**
	 * The directory for the SSTables
	 */
	protected final String directory;
	
	/**
	 * SSTable output stream
	 */
	protected FileOutputStream sstableOutputStream;
	
	/**
	 * SSTable index stream
	 */
	protected FileOutputStream sstableIndexOutputStream;
	
	/**
	 * The SSTable file object
	 */
	protected File sstableFile;
	
	/**
	 * The SSTable index file object
	 */
	protected File sstableIndexFile;
	
	/**
	 * The meta data file
	 */
	protected File metadatafile;
	
	/**
	 * A counter for the written tuples
	 */
	protected SSTableMetadataBuilder metadataBuilder;
	
	/**
	 * The error flag
	 */
	protected boolean exceptionDuringWrite;
	
	/**
	 * The Logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(SSTableWriter.class);
	
	public SSTableWriter(final String directory, final SSTableName name, final int tablenumber) {
		this.directory = directory;
		this.name = name;
		this.tablebumber = tablenumber;
		
		this.sstableOutputStream = null;
		this.metadataBuilder = new SSTableMetadataBuilder();
		
		final String ssTableMetadataFilename = SSTableHelper.getSSTableMetadataFilename(directory, name.getFullname(), tablebumber);
		this.metadatafile = new File(ssTableMetadataFilename);
		
		this.exceptionDuringWrite = false;
	}
	
	/**
	 * Open all required files
	 * @throws StorageManagerException
	 */
	public void open() throws StorageManagerException {
		final String directoryName = SSTableHelper.getSSTableDir(directory, name.getFullname());
		final File directoryHandle = new File(directoryName);
		
		if(! directoryHandle.isDirectory()) {
			final String error = "Directory for SSTable " + name + " does not exist: " + directoryName;
			logger.error(error);
			throw new StorageManagerException(error);
		}
		
		final String sstableOutputFileName = SSTableHelper.getSSTableFilename(directory, name.getFullname(), tablebumber);
		sstableFile = new File(sstableOutputFileName);
		
		final String outputIndexFileName = SSTableHelper.getSSTableIndexFilename(directory, name.getFullname(), tablebumber);
		sstableIndexFile = new File(outputIndexFileName);
		
		// Don't overwrite old data
		if(sstableFile.exists()) {
			throw new StorageManagerException("Table file already exists: " + sstableOutputFileName);
		}
		
		if(sstableIndexFile.exists()) {
			throw new StorageManagerException("Table file already exists: " + sstableIndexFile);
		}
		
		try {
			logger.info("Opening new SSTable for relation: " + name + " file: " + sstableOutputFileName);
			sstableOutputStream = new FileOutputStream(sstableFile);
			sstableOutputStream.write(SSTableConst.MAGIC_BYTES);
			sstableIndexOutputStream = new FileOutputStream(sstableIndexFile);
			sstableIndexOutputStream.write(SSTableConst.MAGIC_BYTES);
		} catch (FileNotFoundException e) {
			exceptionDuringWrite = true;
			throw new StorageManagerException("Unable to open output file", e);
		} catch (IOException e) {
			exceptionDuringWrite = true;
			throw new StorageManagerException("Unable to write into output file", e);
		}
	}
	
	/**
	 * Close all open file handles and write the meta data
	 */
	public void close() throws StorageManagerException {
		try {			
			logger.info("Closing new SSTable for relation: " + name + " number " + tablebumber + " File: " + sstableFile.getName());

			if(sstableOutputStream != null) {
				sstableOutputStream.close();
				sstableOutputStream = null;
			}
			
			if(sstableIndexOutputStream != null) {
				sstableIndexOutputStream.close();
				sstableIndexOutputStream = null;
			}
			
			writeMetadata();	
		} catch (IOException e) {
			exceptionDuringWrite = true;
			throw new StorageManagerException("Exception while closing streams", e);
		} finally {
			handleWriteException();
		}
	}

	/**
	 *  Delete half written files if an exception has occurred
	 *  The variable exceptionDuringWrite is set to true in every catch block
	 */
	protected void handleWriteException() {
		if(exceptionDuringWrite == true) {
			if(sstableFile != null && sstableFile.exists()) {
				sstableFile.delete();
			}
			
			if(sstableIndexFile != null && sstableIndexFile.exists()) {
				sstableIndexFile.delete();
			}
			
			if(metadatafile != null && metadatafile.exists()) {
				metadatafile.delete();
			}
		}
	}
	
	/**
	 * Write the meta data to yaml info file
	 * @throws IOException
	 */
	protected void writeMetadata() throws IOException {
		final SStableMetaData metadata = metadataBuilder.getMetaData();
		metadata.exportToYamlFile(metadatafile);
	}
	
	/**
	 * Format of a data record:
	 * 
	 * ----------------------------------------------------------------------------------
	 * | Key-Length | BBox-Length | Data-Length | Timestamp |   Key   |  BBox  |  Data  |
	 * |   2 Byte   |    4 Byte   |    4 Byte   |   8 Byte  |         |        |        |
	 * ----------------------------------------------------------------------------------
	 * 
	 * @param tuples
	 * @throws StorageManagerException
	 */
	public void addData(final List<Tuple> tuples) throws StorageManagerException {
		if(sstableOutputStream == null) {
			final String error = "Trying to add a memtable to a non ready SSTable writer";
			logger.error(error);
			throw new StorageManagerException(error);
		}
		
		try {
			for(final Tuple tuple : tuples) {
				addNextTuple(tuple);
			}
		} catch(StorageManagerException e) {
			exceptionDuringWrite = true;
			throw e;
		}
	}

	/**
	 * Add the next tuple into the result sstable
	 * @param tuple
	 * @throws IOException
	 * @throws StorageManagerException 
	 */
	public void addNextTuple(final Tuple tuple) throws StorageManagerException {
		try {
			// Add Tuple to the index
			long tuplePosition = sstableOutputStream.getChannel().position();
			writeIndexEntry((int) tuplePosition);
			
			// Add Tuple to the SSTable file
			writeTupleToFile(tuple);
			metadataBuilder.addTuple(tuple);
		} catch (IOException e) {
			exceptionDuringWrite = true;
			throw new StorageManagerException("Untable to write memtable to SSTable", e);
		}
	}

	/**
	 * Write the given tuple into the SSTable
	 * @param tuple
	 * @throws IOException
	 */
	protected void writeTupleToFile(final Tuple tuple) throws IOException {
		final byte[] keyBytes = tuple.getKey().getBytes();
		final ByteBuffer keyLengthBytes = DataEncoderHelper.shortToByteBuffer((short) keyBytes.length);

		final byte[] boundingBoxBytes = tuple.getBoundingBoxBytes();
		final byte[] data = tuple.getDataBytes();
		
		final ByteBuffer boxLengthBytes = DataEncoderHelper.intToByteBuffer(boundingBoxBytes.length);
		final ByteBuffer dataLengthBytes = DataEncoderHelper.intToByteBuffer(data.length);
	    final ByteBuffer timestampBytes = DataEncoderHelper.longToByteBuffer(tuple.getTimestamp());
	    
	    sstableOutputStream.write(keyLengthBytes.array());
		sstableOutputStream.write(boxLengthBytes.array());
		sstableOutputStream.write(dataLengthBytes.array());
		sstableOutputStream.write(timestampBytes.array());
		sstableOutputStream.write(keyBytes);
		sstableOutputStream.write(boundingBoxBytes);
		sstableOutputStream.write(data);
	}

	/** 
	 * Append an entry to the index file.
	 * 
	 * Format of the index file:
	 * 
	 * -------------------------------------------------
	 * | Tuple-Position | Tuple-Position |  .........  |
 	 * |     4 Byte     |     4 Byte     |  .........  |
	 * -------------------------------------------------
	 * 
	 * @param keyLengthBytes
	 * @param keyPosition
	 * @throws IOException
	 */
	protected void writeIndexEntry(int tuplePosition) throws IOException {
		final ByteBuffer tuplePositionBytes = DataEncoderHelper.intToByteBuffer(tuplePosition);
		sstableIndexOutputStream.write(tuplePositionBytes.array());
	}

	/**
	 * Get the sstable output file
	 * @return
	 */
	public File getSstableFile() {
		return sstableFile;
	}

	/**
	 * Get the sstable index output file
	 * @return
	 */
	public File getSstableIndexFile() {
		return sstableIndexFile;
	}
	
	/**
	 * Does an exception during a write operation occured?
	 * @return
	 */
	public boolean isErrorFlagSet() {
		return exceptionDuringWrite;
	}
	
}
