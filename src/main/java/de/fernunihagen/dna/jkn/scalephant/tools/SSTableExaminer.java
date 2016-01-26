package de.fernunihagen.dna.jkn.scalephant.tools;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;

import de.fernunihagen.dna.jkn.scalephant.storage.StorageConfiguration;
import de.fernunihagen.dna.jkn.scalephant.storage.StorageManagerException;
import de.fernunihagen.dna.jkn.scalephant.storage.Tuple;
import de.fernunihagen.dna.jkn.scalephant.storage.sstable.SSTableIndexReader;
import de.fernunihagen.dna.jkn.scalephant.storage.sstable.SSTableReader;

public class SSTableExaminer implements Runnable {

	protected String filename;
	protected String relationname;
	protected String examineKey;
	
	public SSTableExaminer(final String relationname, final String filename, final String examineKey) {
		super();
		this.filename = filename;
		this.relationname = relationname;
		this.examineKey = examineKey;
	}

	@Override
	public void run() {
		try {
			final StorageConfiguration storageConfiguration = new StorageConfiguration();
			final SSTableReader ssTableReader = new SSTableReader(relationname, storageConfiguration.getDataDir(), new File(filename));
			
			final SSTableIndexReader ssTableIndexReader = new SSTableIndexReader(ssTableReader);
			
			ssTableReader.init();
			ssTableIndexReader.init();
			
			if(! ssTableReader.acquire()) {
				throw new StorageManagerException("Unable to acquire sstable reader");
			}
			
			if(! ssTableIndexReader.acquire()) {
				throw new StorageManagerException("Unable to acquire sstable index reader");
			}
			
			fullTableScan(ssTableReader);
			internalScan(ssTableReader);
			seachViaIndex(ssTableReader, ssTableIndexReader);
			
			ssTableIndexReader.release();
			ssTableReader.release();
			ssTableIndexReader.shutdown();
			ssTableReader.shutdown();
		} catch (StorageManagerException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}

	/**
	 * Search our tuple via index
	 * @param ssTableReader
	 * @param ssTableIndexReader
	 * @throws StorageManagerException
	 */
	protected void seachViaIndex(final SSTableReader ssTableReader,
			final SSTableIndexReader ssTableIndexReader)
			throws StorageManagerException {
		
		System.out.println("Step3: Seach via index");
		int pos = ssTableIndexReader.getPositionForTuple(examineKey);
		System.out.println("Got index pos: " + pos);
		
		// Tuple found
		if(pos != -1) {
			System.out.println(ssTableReader.getTupleAtPosition(pos));
		}
	}

	/**
	 * Perform a scan with the internal method of the sstable rader
	 * @param ssTableReader
	 * @throws StorageManagerException
	 */
	protected void internalScan(final SSTableReader ssTableReader)
			throws StorageManagerException {
		
		System.out.println("Step2: Scan for tuple with key: " + examineKey);
		final Tuple scanTuple = ssTableReader.scanForTuple(examineKey);
		System.out.println(scanTuple);
	}

	/**
	 * Perform a full table scan
	 * @param ssTableReader
	 * @throws IOException
	 */
	protected void fullTableScan(final SSTableReader ssTableReader)
			throws IOException {
		
		System.out.println("Step1: Looping over SSTable and searching for key: " + examineKey);
		while(true) {
			try {
				final Tuple tuple = ssTableReader.decodeTuple();
				if(tuple.getKey().equals(examineKey)) {
					System.out.println(tuple);
				}
				
			} catch (BufferUnderflowException e) {
				// Loop until the buffer is empty
				break;
			}
		}
	}
	
	/**
	 * Main * Main * Main 
	 * 
	 * Examine a given SSTable and the coresponding index. The tuple with the key=examineKey
	 * will be search with fulltable scans and index scans. The result of this operations
	 * is printed onto the console.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		final String relationname = "testrelation";
		final String filename = "/tmp/scalephant/data/testrelation/sstable_testrelation_78.sst";
		final String examineKey = "2555";
		final SSTableExaminer dumper = new SSTableExaminer(relationname, filename, examineKey);
		dumper.run();
	}
}
