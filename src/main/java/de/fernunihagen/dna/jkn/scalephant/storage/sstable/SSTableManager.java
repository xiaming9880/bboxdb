package de.fernunihagen.dna.jkn.scalephant.storage.sstable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fernunihagen.dna.jkn.scalephant.ScalephantConfiguration;
import de.fernunihagen.dna.jkn.scalephant.ScalephantService;
import de.fernunihagen.dna.jkn.scalephant.storage.Memtable;
import de.fernunihagen.dna.jkn.scalephant.storage.StorageManagerException;
import de.fernunihagen.dna.jkn.scalephant.storage.entity.BoundingBox;
import de.fernunihagen.dna.jkn.scalephant.storage.entity.DeletedTuple;
import de.fernunihagen.dna.jkn.scalephant.storage.entity.Tuple;
import de.fernunihagen.dna.jkn.scalephant.storage.sstable.reader.SSTableFacade;
import de.fernunihagen.dna.jkn.scalephant.storage.sstable.reader.SSTableKeyIndexReader;
import de.fernunihagen.dna.jkn.scalephant.storage.sstable.reader.SSTableReader;
import de.fernunihagen.dna.jkn.scalephant.util.State;

public class SSTableManager implements ScalephantService {
	
	/**
	 * The name of the table
	 */
	private final String name;
	
	/**
	 * The Storage configuration
	 */
	private final ScalephantConfiguration storageConfiguration;
	
	/**
	 * The number of the table
	 */
	private AtomicInteger tableNumber;
	
	/**
	 * The reader for existing SSTables
	 */
	protected final List<SSTableFacade> sstableFacades;

	/**
	 * The unflushed memtables
	 */
	protected List<Memtable> unflushedMemtables;

	/**
	 * Ready flag for flush thread
	 */
	protected volatile boolean ready;
	
	/**
	 * The Flush thread
	 */
	protected Thread flushThread;
	
	/**
	 * The compactification thread
	 */
	protected Thread compactThread;
	
	/**
	 * The corresponding storage manager state
	 */
	protected State storageState;
	
	/**
	 * The logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(SSTableManager.class);

	public SSTableManager(final State storageState, final String name, final ScalephantConfiguration storageConfiguration) {
		super();

		this.storageConfiguration = storageConfiguration;
		this.storageState = storageState;
		this.name = name;
		this.tableNumber = new AtomicInteger();
		this.tableNumber.set(0);
		ready = false;
		
		unflushedMemtables = new CopyOnWriteArrayList<Memtable>();
		sstableFacades = new CopyOnWriteArrayList<SSTableFacade>();
	}

	/**
	 * Init the instance
	 * 
	 */
	@Override
	public void init() {
		
		if(ready == true) {
			logger.warn("SSTable manager is active and init() is called");
			return;
		}
		
		logger.info("Init a new instance for the table: " + getName());
		unflushedMemtables.clear();
		sstableFacades.clear();
		createSSTableDirIfNeeded();
		
		try {
			scanForExistingTables();
		} catch (StorageManagerException e) {
			// Unable to init the instance
			return;
		}
		
		this.tableNumber = new AtomicInteger();
		this.tableNumber.set(getLastSequencenumberFromReader());

		// Set to ready before the threads are started
		ready = true;

		if(storageConfiguration.isStorageRunMemtableFlushThread()) {
			flushThread = new Thread(new SSTableFlushThread(this));
			flushThread.setName("Memtable flush thread for: " + getName());
			flushThread.start();
		} else {
			logger.info("NOT starting the memtable flush thread.");
		}
		
		if(storageConfiguration.isStorageRunCompactThread()) {
			compactThread = new Thread(new SSTableCompactorThread(this));
			compactThread.setName("Compact thread for: " + getName());
			compactThread.start();
		} else {
			logger.info("NOT starting the sstable compact thread.");
		}
	}

	/**
	 * Shutdown the instance
	 * 
	 */
	@Override
	public void shutdown() {
		logger.info("Shuting down the instance for table: " + getName());
		ready = false;
		
		if(flushThread != null) {
			flushThread.interrupt();
		}
		
		if(compactThread != null) {
			compactThread.interrupt();
		}
		
		for(final SSTableFacade facade : sstableFacades) {
			facade.shutdown();
		}
		sstableFacades.clear();
	}
	
	
	/**
	 * Is the shutdown complete?
	 * 
	 * @return
	 */
	public boolean isShutdownComplete() {
		boolean shutdownComplete = true;
		
		if(flushThread != null) {
			shutdownComplete = shutdownComplete & ! flushThread.isAlive();
		}
		
		if(compactThread != null) {
			shutdownComplete = shutdownComplete & ! compactThread.isAlive();
		}
		
		return shutdownComplete;
	}
	
	/**
	 * Ensure that the directory for the given table exists
	 * 
	 */
	protected void createSSTableDirIfNeeded() {
		final File rootDir = new File(storageConfiguration.getDataDirectory());		
		final File directoryHandle = new File(SSTableHelper.getSSTableDir(storageConfiguration.getDataDirectory(), getName()));
		
		if(rootDir.exists() && ! directoryHandle.exists()) {
			logger.info("Create a new dir for table: " + getName());
			directoryHandle.mkdir();
		}
	}
	
	/**
	 * Scan the database directory for all existing SSTables and
	 * create reader objects
	 * @throws StorageManagerException 
	 * 
	 */
	protected void scanForExistingTables() throws StorageManagerException {
		logger.info("Scan for existing SSTables: " + getName());
		final File directoryHandle = new File(SSTableHelper.getSSTableDir(storageConfiguration.getDataDirectory(), getName()));
		
	    checkSSTableDir(directoryHandle);
	
		final File[] entries = directoryHandle.listFiles();
				
		for(final File file : entries) {
			final String filename = file.getName();
			if(isFileNameSSTable(filename)) {
				logger.info("Found sstable: " + filename);
				
				try {
					final int sequenceNumber = SSTableHelper.extractSequenceFromFilename(name, filename);
					final SSTableFacade facade = new SSTableFacade(storageConfiguration.getDataDirectory(), getName(), sequenceNumber);
					facade.init();
					sstableFacades.add(facade);
				} catch(StorageManagerException e) {
					logger.warn("Unable to parse sequence number, ignoring file: " + filename, e);
				}
			}
		}
	}
	
	/**
	 * Get the highest sequence number, based on the reader
	 * instances
	 * 
	 * @return the sequence number
	 */
	protected int getLastSequencenumberFromReader() {
		
		int number = 0;
		
		for(final SSTableFacade facade : sstableFacades) {
			final int sequenceNumber = facade.getTablebumber();
			
			if(sequenceNumber >= number) {
				number = sequenceNumber + 1;
			}
		}
		
		return number;
	}

	/**
	 * Ensure that the storage directory does exist
	 * 
	 * @param directoryHandle
	 * @throws StorageManagerException 
	 */
	public void checkSSTableDir(final File directoryHandle) throws StorageManagerException {
		if(! directoryHandle.isDirectory()) {
			final String message = "Storage directory is not an directory: " + directoryHandle;
			storageState.setReady(false);
			logger.error(message);
			throw new StorageManagerException(message);
		}		
	}
	
	/**
	 * Delete all existing SSTables in the given directory
	 * 
	 * @return Directory was deleted or not
	 * @throws StorageManagerException 
	 */
	public boolean deleteExistingTables() throws StorageManagerException {
		logger.info("Delete all existing SSTables for relation: " + getName());
		final File directoryHandle = new File(SSTableHelper.getSSTableDir(storageConfiguration.getDataDirectory(), getName()));
	
		// Does the directory exist?
		if(! directoryHandle.isDirectory()) {
			return true;
		}

		final File[] entries = directoryHandle.listFiles();
				
		for(final File file : entries) {
			final String filename = file.getName();
			if(isFileNameSSTable(filename)) {
				logger.info("Deleting file: " + file);
				file.delete();
			} else if(isFileNameSSTableIndex(filename)) {
				logger.info("Deleting index file: " + file);
				file.delete();
			} else if(isFileNameSSTableMetadata(filename)) {
				logger.info("Deleting meta file: " + file);
				file.delete();
			}
		}
		
		// Delete the directory if empty
		if(directoryHandle.listFiles().length != 0) {
			logger.info("SStable directory is not empty, skip directory delete");
			return false;
		} else {
			directoryHandle.delete();
			return true;
		}
	}

	/**
	 * Belongs the given filename to a SSTable?
	 * 
	 * @param filename
	 * @return
	 */
	protected boolean isFileNameSSTable(final String filename) {
		return filename.startsWith(SSTableConst.SST_FILE_PREFIX) 
				&& filename.endsWith(SSTableConst.SST_FILE_SUFFIX);
	}
	
	/**
	 * Belongs the given filename to a SSTable index?
	 * 
	 * @param filename
	 * @return
	 */
	protected boolean isFileNameSSTableIndex(final String filename) {
		return filename.startsWith(SSTableConst.SST_FILE_PREFIX) 
				&& filename.endsWith(SSTableConst.SST_INDEX_SUFFIX);
	}
	
	/**
	 * Belongs the given filename to a SSTable meta file?
	 * @param filename
	 * @return
	 */
	protected boolean isFileNameSSTableMetadata(final String filename) {
		return filename.startsWith(SSTableConst.SST_FILE_PREFIX) 
				&& filename.endsWith(SSTableConst.SST_META);
	}
	
	/**
	 * Schedule a memtable for flush
	 * 
	 * @param memtable
	 * @throws StorageManagerException
	 */
	public void flushMemtable(final Memtable memtable) throws StorageManagerException {
		synchronized (unflushedMemtables) {
			unflushedMemtables.add(memtable);
			unflushedMemtables.notifyAll();
		}
	}
	
	/**
	 * Search for the most recent version of the tuple
	 * @param key
	 * @return The tuple or null
	 * @throws StorageManagerException
	 */
	public Tuple get(final String key) throws StorageManagerException {
			
		// Read unlushed memtables first
		Tuple tuple = getTupleFromMemtable(key);
				
		boolean readComplete = false;
		while(! readComplete) {
			readComplete = true;
		
			// Read data from the persistent SSTables
			for(final SSTableFacade facade : sstableFacades) {
				boolean canBeUsed = facade.acquire();
				
				if(! canBeUsed ) {
					readComplete = false;
					break;
				}
				
				final SSTableKeyIndexReader indexReader = facade.getSsTableKeyIndexReader();
				final SSTableReader reader = facade.getSsTableReader();
				
				final int position = indexReader.getPositionForTuple(key);
				
				// Found a tuple
				if(position != -1) {
					final Tuple tableTuple = reader.getTupleAtPosition(position);
					if(tuple == null) {
						tuple = tableTuple;
					} else if(tableTuple.getTimestamp() > tuple.getTimestamp()) {
						tuple = tableTuple;
					}
				}
				
				facade.release();
			}
		}
		
		if(tuple instanceof DeletedTuple) {
			return null;
		}
		
		return tuple;
	}
	
	/**
	 * Get all tuples that are inside of the bounding box
	 * @param boundingBox
	 * @return
	 * @throws StorageManagerException 
	 */
	public Collection<Tuple> getTuplesInside(final BoundingBox boundingBox) throws StorageManagerException {
		final List<Tuple> resultList = new ArrayList<Tuple>();
		
		// Read unflushed memtables first
		for(final Memtable unflushedMemtable : unflushedMemtables) {
			try {
				final Collection<Tuple> memtableResult = unflushedMemtable.getTuplesInside(boundingBox);
				resultList.addAll(memtableResult);
			} catch (StorageManagerException e) {
				logger.warn("Got an exception while scanning unflushed memtable: ", e);
			}
		}
		
		// Scan the sstables
		boolean readComplete = false;
		while(! readComplete) {
			readComplete = true;
		
			// Read data from the persistent SSTables
			for(final SSTableFacade facade : sstableFacades) {
				boolean canBeUsed = facade.acquire();
				
				if(! canBeUsed ) {
					readComplete = false;
					break;
				}
				
				final SSTableKeyIndexReader indexReader = facade.getSsTableKeyIndexReader();
								
				for (final Tuple tuple : indexReader) {
					if(tuple.getBoundingBox().overlaps(boundingBox)) {
						resultList.add(tuple);
					}
				}
				
				facade.release();
			}
		}
		
		return resultList;
	}

	/**
	 * Get the tuple from the unflushed memtables
	 * @param key
	 * @return
	 */
	protected Tuple getTupleFromMemtable(final String key) {
		
		Tuple result = null;
		
		for(final Memtable unflushedMemtable : unflushedMemtables) {
			final Tuple tuple = unflushedMemtable.get(key);
			
			if(tuple != null) {
				if(result == null) {
					result = tuple;
					continue;
				}
				
				// Get the most recent version of the tuple
				if(tuple.compareTo(result) < 0) {
					result = tuple;
					continue;	
				}
			}
		}
		
		return result;
	}
	
	public int increaseTableNumber() {
		return tableNumber.getAndIncrement();
	}

	public boolean isReady() {
		return ready;
	}

	public void setReady(boolean ready) {
		this.ready = ready;
	}

	public String getName() {
		return name;
	}

	/**
	 * Returns the configuration
	 * @return
	 */
	public ScalephantConfiguration getStorageConfiguration() {
		return storageConfiguration;
	}

	@Override
	public String getServicename() {
		return "SSTable manager";
	}

	public List<SSTableFacade> getSstableFacades() {
		return sstableFacades;
	}
}
