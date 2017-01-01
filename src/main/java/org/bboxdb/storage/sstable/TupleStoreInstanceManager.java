/*******************************************************************************
 *
 *    Copyright (C) 2015-2017 the BBoxDB project
 *  
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License. 
 *    
 *******************************************************************************/
package org.bboxdb.storage.sstable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bboxdb.storage.Memtable;
import org.bboxdb.storage.ReadOnlyTupleStorage;
import org.bboxdb.storage.sstable.reader.SSTableFacade;

/**
 * This class holds references to all known tuple storages (sstables, memtables) for 
 * a certain sstable 
 *
 */
public class TupleStoreInstanceManager {
	
	/**
	 * The active memtable
	 */
	protected Memtable memtable;
	
	/**
	 * The unflushed memtables
	 */
	protected final List<Memtable> unflushedMemtables;
	
	/**
	 * The reader for existing SSTables
	 */
	protected final List<SSTableFacade> sstableFacades;
	
	/**
	 * The queue for the memtable flush thread
	 */
	protected final Queue<Memtable> memtablesToFlush;
	
	public TupleStoreInstanceManager() {
		sstableFacades = new ArrayList<SSTableFacade>();
		unflushedMemtables = new ArrayList<Memtable>();
		memtablesToFlush = new ConcurrentLinkedQueue<Memtable>();
	}
	
	/**
	 * Activate a new memtable, the old memtable is transfered into the
	 * unflushed memtable list and also pushed into the flush queue
	 * @param newMemtable
	 */
	public synchronized void activateNewMemtable(final Memtable newMemtable) {
		
		if(memtable != null) {
			unflushedMemtables.add(memtable);
			
			synchronized (memtablesToFlush) {
				memtablesToFlush.add(memtable);
				memtablesToFlush.notifyAll();
			}
			
		}
		
		memtable = newMemtable;
	}
	
	/**
	 * After the flush, the memtable can be replaced with an sstable facade
	 * @param memtable
	 * @param sstableFacade
	 */
	public synchronized void replaceMemtableWithSSTable(final Memtable memtable, 
			final SSTableFacade sstableFacade) {
		
		sstableFacades.add(sstableFacade);
		unflushedMemtables.remove(memtable);
	}
	
	/**
	 * Replace some compaced and merged sstables
	 * @param newSStable
	 * @param oldFacades
	 */
	public synchronized void replaceCompactedSStables(final SSTableFacade newSStable, 
			final List<SSTableFacade> oldFacades) {
		
		sstableFacades.add(newSStable);
		
		for(final SSTableFacade facade : oldFacades) {
			sstableFacades.remove(facade);
		}
	}
	
	/**
	 * Add a new SSTable
	 * @param newSStable
	 */
	public synchronized void addNewDetectedSSTable(final SSTableFacade newSStable) {
		sstableFacades.add(newSStable);
	}
	
	/**
	 * Get a list with all active storages
	 * @return
	 */
	public synchronized List<ReadOnlyTupleStorage> getAllTupleStorages() {
		final List<ReadOnlyTupleStorage> allStorages = new ArrayList<ReadOnlyTupleStorage>();
		allStorages.add(memtable);
		allStorages.addAll(unflushedMemtables);
		allStorages.addAll(sstableFacades);
		
		return allStorages;
	}
	
	/**
	 * Clear all known tuple stores
	 */
	public synchronized void clear() {
		memtable = null;
		memtablesToFlush.clear();
		sstableFacades.clear();
		unflushedMemtables.clear();
	}
	
	/**
	 * Get the memtable flush queue
	 * @return
	 */
	public Queue<Memtable> getMemtablesToFlush() {
		return memtablesToFlush;
	}

	/**
	 * Get the active memtable
	 * @return
	 */
	public Memtable getMemtable() {
		return memtable;
	}
	
	/**
	 * Get the sstable facades
	 * @return
	 */
	public synchronized Collection<SSTableFacade> getSstableFacades() {
		return Collections.unmodifiableCollection(sstableFacades);
	}
	
	/**
	 * Get all in memory storages
	 * @return 
	 */
	public synchronized List<ReadOnlyTupleStorage> getAllInMemoryStorages() {
		final List<ReadOnlyTupleStorage> resultList = new ArrayList<ReadOnlyTupleStorage>();
		resultList.add(memtable);
		resultList.addAll(unflushedMemtables);
		return resultList;
	}
}
