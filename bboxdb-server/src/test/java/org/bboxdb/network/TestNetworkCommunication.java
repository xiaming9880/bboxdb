/*******************************************************************************
 *
 *    Copyright (C) 2015-2018 the BBoxDB project
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
package org.bboxdb.network;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.bboxdb.BBoxDBMain;
import org.bboxdb.commons.math.BoundingBox;
import org.bboxdb.misc.BBoxDBConfigurationManager;
import org.bboxdb.misc.BBoxDBException;
import org.bboxdb.network.client.BBoxDB;
import org.bboxdb.network.client.BBoxDBClient;
import org.bboxdb.network.client.future.EmptyResultFuture;
import org.bboxdb.network.client.future.TupleListFuture;
import org.bboxdb.storage.entity.Tuple;
import org.bboxdb.storage.entity.TupleStoreConfiguration;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.Lists;

public class TestNetworkCommunication {

	/**
	 * The instance of the software
	 */
	private static BBoxDBMain bboxDBMain;
	
	/**
	 * The replication factor for the unit tests
	 */
	public final static short REPLICATION_FACTOR = 1;

	private static final String DISTRIBUTION_GROUP = "testgroup";
	
	@BeforeClass
	public static void init() throws Exception {
		bboxDBMain = new BBoxDBMain();
		bboxDBMain.init();
		bboxDBMain.start();
		
		Thread.currentThread();
		// Wait some time to let the server process start
		Thread.sleep(5000);
	}
	
	@AfterClass
	public static void shutdown() throws Exception {
		if(bboxDBMain != null) {
			bboxDBMain.stop();
			bboxDBMain = null;
		}
		
		// Wait some time for socket re-use
		Thread.sleep(5000);
	}
	
	/**
	 * Re-create distribution group for each test
	 * @throws InterruptedException
	 * @throws BBoxDBException 
	 */
	@Before
	public void before() throws InterruptedException, BBoxDBException {
		final BBoxDB bboxdbClient = connectToServer();
		TestHelper.recreateDistributionGroup(bboxdbClient, DISTRIBUTION_GROUP);
		disconnect(bboxdbClient);
	}
	
	/**
	 * Integration test for the disconnect package
	 * 
	 */
	@Test(timeout=60000)
	public void testSendDisconnectPackage() {
		System.out.println("=== Running testSendDisconnectPackage");

		final BBoxDBClient bboxDBClient = connectToServer();
		Assert.assertTrue(bboxDBClient.isConnected());
		disconnect(bboxDBClient);
		Assert.assertFalse(bboxDBClient.isConnected());
		disconnect(bboxDBClient);
		
		System.out.println("=== End testSendDisconnectPackage");
	}
	
	/**
	 * Test the double connect call
	 */
	@Test(timeout=60000)
	public void testDoubleConnect() {
		final BBoxDBClient bboxDBClient = connectToServer();
		Assert.assertTrue(bboxDBClient.isConnected());
		Assert.assertTrue(bboxDBClient.connect());
		Assert.assertTrue(bboxDBClient.isConnected());
		disconnect(bboxDBClient);
	}
	
	/**
	 * Test the double disconnect call
	 */
	@Test(timeout=60000)
	public void testDoubleDisconnect() {
		final BBoxDBClient bboxDBClient = connectToServer();
		disconnect(bboxDBClient);
		disconnect(bboxDBClient);
	}
	
	/**
	 * Send a delete package to the server
	 * @throws InterruptedException 
	 * @throws ExecutionException 
	 */
	@Test(timeout=60000)
	public void sendDeletePackage() throws InterruptedException, ExecutionException {
		System.out.println("=== Running sendDeletePackage");

		final BBoxDBClient bboxDBClient = connectToServer();
		
		final EmptyResultFuture result = bboxDBClient.deleteTable(DISTRIBUTION_GROUP + "_relation3");
		
		result.waitForAll();
		
		Assert.assertTrue(result.isDone());
		Assert.assertFalse(result.isFailed());
		Assert.assertTrue(bboxDBClient.getConnectionState().isInRunningState());
		
		disconnect(bboxDBClient);
		Assert.assertFalse(bboxDBClient.isConnected());
		disconnect(bboxDBClient);

		System.out.println("=== End sendDeletePackage");
	}
	
	/**
	 * Test the state machine of the connection
	 */
	@Test(timeout=60000)
	public void testConnectionState() {
		System.out.println("=== Running testConnectionState");

		final int port = BBoxDBConfigurationManager.getConfiguration().getNetworkListenPort();
		final BBoxDBClient bboxDBClient = new BBoxDBClient(new InetSocketAddress("127.0.0.1", port));
		Assert.assertTrue(bboxDBClient.getConnectionState().isInNewState());
		bboxDBClient.connect();
		Assert.assertTrue(bboxDBClient.getConnectionState().isInRunningState());
		bboxDBClient.disconnect();
		Assert.assertTrue(bboxDBClient.getConnectionState().isInTerminatedState());
		disconnect(bboxDBClient);

		System.out.println("=== End testConnectionState");
	}
	
	/**
	 * Send a delete package to the server
	 * @throws InterruptedException 
	 * @throws ExecutionException 
	 */
	@Test(timeout=60000)
	public void sendDeletePackage2() throws InterruptedException, ExecutionException {
		System.out.println("=== Running sendDeletePackage2");
		
		final String table = DISTRIBUTION_GROUP + "_relation3";

		final BBoxDBClient bboxDBClient = connectToServer();
		
		// First call
		final EmptyResultFuture result1 = bboxDBClient.deleteTable(table);
		result1.waitForAll();
		Assert.assertTrue(result1.isDone());
		Assert.assertFalse(result1.isFailed());
		Assert.assertTrue(bboxDBClient.getConnectionState().isInRunningState());
		
		// Wait for command processing
		Thread.sleep(1000);
		
		// Second call
		final EmptyResultFuture result2 = bboxDBClient.deleteTable(table);
		result2.waitForAll();
		Assert.assertTrue(result2.isDone());
		Assert.assertFalse(result2.isFailed());
		Assert.assertTrue(bboxDBClient.getConnectionState().isInRunningState());
		
		disconnect(bboxDBClient);
		Assert.assertFalse(bboxDBClient.isConnected());		
		disconnect(bboxDBClient);

		System.out.println("=== End sendDeletePackage2");
	}

	/**
	 * The the insert and the deletion of a tuple
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * @throws BBoxDBException 
	 */
	@Test(timeout=60000)
	public void testInsertAndDelete() throws InterruptedException, ExecutionException, BBoxDBException {
		final BBoxDBClient bboxdbClient = connectToServer();
		NetworkQueryHelper.testInsertAndDeleteTuple(bboxdbClient, DISTRIBUTION_GROUP);
		disconnect(bboxdbClient);
	}
	
	/**
	 * Insert some tuples and start a bounding box query afterwards
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * @throws BBoxDBException 
	 */
	@Test(timeout=60000)
	public void testInsertAndBoundingBoxQuery() throws InterruptedException, ExecutionException, BBoxDBException {
		final BBoxDBClient bboxDBClient = connectToServer();

		NetworkQueryHelper.testBoundingBoxQuery(bboxDBClient, DISTRIBUTION_GROUP);
		disconnect(bboxDBClient);
	}
	
	/**
	 * Insert some tuples and start a bounding box query afterwards
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * @throws BBoxDBException 
	 */
	@Test(timeout=60000)
	public void testInsertAndBoundingBoxContinousQuery() throws InterruptedException, ExecutionException, BBoxDBException {
		final BBoxDBClient bboxDBClient = connectToServer();

		NetworkQueryHelper.testBoundingBoxQueryContinous(bboxDBClient, DISTRIBUTION_GROUP);
		disconnect(bboxDBClient);
	}
	
	/**
	 * Execute the version time query
	 * @throws BBoxDBException 
	 * @throws InterruptedException 
	 */
	@Test(timeout=60000)
	public void testVersionTimeQuery() throws InterruptedException, BBoxDBException {
		final BBoxDBClient bboxDBClient = connectToServer();

		NetworkQueryHelper.testVersionTimeQuery(bboxDBClient, DISTRIBUTION_GROUP);
		disconnect(bboxDBClient);
	}
	
	/**
	 * Execute the version inserted query
	 * @throws BBoxDBException 
	 * @throws InterruptedException 
	 */
	@Test(timeout=60000)
	public void testInsertedTimeQuery() throws InterruptedException, BBoxDBException {
		final BBoxDBClient bboxDBClient = connectToServer();

		NetworkQueryHelper.testInsertedTimeQuery(bboxDBClient, DISTRIBUTION_GROUP);
		disconnect(bboxDBClient);
	}
	
	/**
	 * Test the tuple join
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * @throws BBoxDBException 
	 */
	@Test(timeout=60000)
	public void testJoin() throws InterruptedException, ExecutionException, BBoxDBException {
		System.out.println("=== Running network testJoin");

		final BBoxDB bboxdbClient = connectToServer();

		NetworkQueryHelper.executeJoinQuery(bboxdbClient, DISTRIBUTION_GROUP);
		
		System.out.println("=== End network testJoin");
		disconnect(bboxdbClient);
	}
	
	/**
	 * Insert some tuples and request it via paging
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * @throws BBoxDBException 
	 */
	@Test(timeout=60000)
	public void testPaging() throws InterruptedException, ExecutionException, BBoxDBException {
		System.out.println("=== Running testPaging");
		final String table = DISTRIBUTION_GROUP + "_relation9999";
		
		final BBoxDBClient bboxDBClient = connectToServer();
		
		// Create table
		final EmptyResultFuture resultCreateTable = bboxDBClient.createTable(table, new TupleStoreConfiguration());
		resultCreateTable.waitForAll();
		Assert.assertFalse(resultCreateTable.isFailed());
		
		// Inside our bbox query
		final Tuple tuple1 = new Tuple("abc", new BoundingBox(0d, 1d, 0d, 1d), "abc".getBytes());
		final EmptyResultFuture result1 = bboxDBClient.insertTuple(table, tuple1);
		
		final Tuple tuple2 = new Tuple("def", new BoundingBox(0d, 0.5d, 0d, 0.5d), "def".getBytes());
		final EmptyResultFuture result2 = bboxDBClient.insertTuple(table, tuple2);
		
		final Tuple tuple3 = new Tuple("geh", new BoundingBox(0.5d, 1.5d, 0.5d, 1.5d), "geh".getBytes());
		final EmptyResultFuture result3 = bboxDBClient.insertTuple(table, tuple3);		
		
		final Tuple tuple4 = new Tuple("ijk", new BoundingBox(-10d, -9d, -10d, -9d), "ijk".getBytes());
		final EmptyResultFuture result4 = bboxDBClient.insertTuple(table, tuple4);
		
		final Tuple tuple5 = new Tuple("lmn", new BoundingBox(1d, 2d, 1d, 2d), "lmn".getBytes());
		final EmptyResultFuture result5 = bboxDBClient.insertTuple(table, tuple5);
		
		result1.waitForAll();
		result2.waitForAll();
		result3.waitForAll();
		result4.waitForAll();
		result5.waitForAll();

		// Without paging
		System.out.println("Pages = unlimited");
		bboxDBClient.setPagingEnabled(false);
		bboxDBClient.setTuplesPerPage((short) 0);
		final TupleListFuture future = bboxDBClient.queryBoundingBox(table, new BoundingBox(-10d, 10d, -10d, 10d));
		future.waitForAll();
		final List<Tuple> resultList = Lists.newArrayList(future.iterator());		
		Assert.assertEquals(5, resultList.size());
		
		// With paging (tuples per page 10)
		System.out.println("Pages = 10");
		bboxDBClient.setPagingEnabled(true);
		bboxDBClient.setTuplesPerPage((short) 10);
		final TupleListFuture future2 = bboxDBClient.queryBoundingBox(table, new BoundingBox(-10d, 10d, -10d, 10d));
		future2.waitForAll();
		final List<Tuple> resultList2 = Lists.newArrayList(future2.iterator());		
		Assert.assertEquals(5, resultList2.size());
		
		// With paging (tuples per page 5)
		System.out.println("Pages = 5");
		bboxDBClient.setPagingEnabled(true);
		bboxDBClient.setTuplesPerPage((short) 5);
		final TupleListFuture future3 = bboxDBClient.queryBoundingBox(table, new BoundingBox(-10d, 10d, -10d, 10d));
		future3.waitForAll();
		final List<Tuple> resultList3 = Lists.newArrayList(future3.iterator());		
		Assert.assertEquals(5, resultList3.size());
		
		// With paging (tuples per page 2)
		System.out.println("Pages = 2");
		bboxDBClient.setPagingEnabled(true);
		bboxDBClient.setTuplesPerPage((short) 2);
		final TupleListFuture future4 = bboxDBClient.queryBoundingBox(table, new BoundingBox(-10d, 10d, -10d, 10d));
		System.out.println("Client is waiting on: " + future4);
		future4.waitForAll();
		final List<Tuple> resultList4 = Lists.newArrayList(future4.iterator());		
		Assert.assertEquals(5, resultList4.size());
		
		// With paging (tuples per page 1)
		System.out.println("Pages = 1");
		bboxDBClient.setPagingEnabled(true);
		bboxDBClient.setTuplesPerPage((short) 1);
		final TupleListFuture future5 = bboxDBClient.queryBoundingBox(table, new BoundingBox(-10d, 10d, -10d, 10d));
		future5.waitForAll();
		final List<Tuple> resultList5 = Lists.newArrayList(future5.iterator());		
		Assert.assertEquals(5, resultList5.size());
		
		System.out.println("=== End testPaging");
		disconnect(bboxDBClient);
	}
	
	/**
	 * Insert a tuple and request it via key
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * @throws BBoxDBException 
	 */
	@Test(timeout=60000)
	public void testGetByKey() throws InterruptedException, ExecutionException, BBoxDBException {
		System.out.println("=== Running testGetByKey");
		final String table = DISTRIBUTION_GROUP + "_relation12333";
		
		final BBoxDBClient bboxDBClient = connectToServer();
		
		// Create table
		final EmptyResultFuture resultCreateTable = bboxDBClient.createTable(table, new TupleStoreConfiguration());
		resultCreateTable.waitForAll();
		Assert.assertFalse(resultCreateTable.isFailed());
		
		// Inside our bbox query
		final Tuple tuple1 = new Tuple("abc", new BoundingBox(0d, 1d, 0d, 1d), "abc".getBytes());
		final EmptyResultFuture result1 = bboxDBClient.insertTuple(table, tuple1);
		
		result1.waitForAll();

		final TupleListFuture future = bboxDBClient.queryKey(table, "abc");
		future.waitForAll();
		
		final List<Tuple> resultList = Lists.newArrayList(future.iterator());		
		Assert.assertEquals(1, resultList.size());
	
		System.out.println("=== End testGetByKey");
		disconnect(bboxDBClient);
	}
	
	/**
	 * Insert some tuples and start a bounding box query afterwards
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * @throws BBoxDBException 
	 */
	@Test(timeout=60000)
	public void testInsertAndBoundingBoxTimeQuery() throws InterruptedException, ExecutionException, BBoxDBException {
		System.out.println("=== Running testInsertAndBoundingBoxTimeQuery");

		final BBoxDBClient bboxDBClient = connectToServer();

		NetworkQueryHelper.executeBoudingboxAndTimeQuery(bboxDBClient, DISTRIBUTION_GROUP);

		System.out.println("=== End testInsertAndBoundingBoxTimeQuery");
		disconnect(bboxDBClient);
	}
	
	/**
	 * Send a keep alive package to the server
	 * @throws InterruptedException 
	 * @throws ExecutionException 
	 */
	@Test(timeout=60000)
	public void sendKeepAlivePackage() throws InterruptedException, ExecutionException {
		System.out.println("=== Running sendKeepAlivePackage");

		final BBoxDBClient bboxDBClient = connectToServer();
		
		final EmptyResultFuture result = bboxDBClient.sendKeepAlivePackage();
		result.waitForAll();
		
		Assert.assertTrue(result.isDone());
		Assert.assertFalse(result.isFailed());
		Assert.assertTrue(bboxDBClient.getConnectionState().isInRunningState());
		
		disconnect(bboxDBClient);
		Assert.assertFalse(bboxDBClient.isConnected());
		
		System.out.println("=== End sendKeepAlivePackage");
		disconnect(bboxDBClient);
	}
	
	/**
	 * Build a new connection to the bboxdb server
	 * 
	 * @return
	 */
	protected BBoxDBClient connectToServer() {
		final int port = BBoxDBConfigurationManager.getConfiguration().getNetworkListenPort();
		final BBoxDBClient bboxDBClient = new BBoxDBClient(new InetSocketAddress("127.0.0.1", port));
		
		if(compressPackages()) {
			bboxDBClient.getClientCapabilities().setGZipCompression();
			Assert.assertTrue(bboxDBClient.getClientCapabilities().hasGZipCompression());
		} else {
			bboxDBClient.getClientCapabilities().clearGZipCompression();
			Assert.assertFalse(bboxDBClient.getClientCapabilities().hasGZipCompression());
		}
		
		Assert.assertFalse(bboxDBClient.isConnected());
		boolean result = bboxDBClient.connect();
		Assert.assertTrue(result);
		Assert.assertTrue(bboxDBClient.isConnected());
		
		if(compressPackages()) { 
			Assert.assertTrue(bboxDBClient.getConnectionCapabilities().hasGZipCompression());
		} else {
			Assert.assertFalse(bboxDBClient.getConnectionCapabilities().hasGZipCompression());
		}
		
		return bboxDBClient;
	}
	
	/**
	 * Test misc methods
	 */
	@Test(timeout=60000)
	public void testMiscMethods() {
		final BBoxDBClient bboxDBClient = connectToServer();
		Assert.assertTrue(bboxDBClient.toString().length() > 10);
		Assert.assertTrue(bboxDBClient.getServerAddress() != null);
		Assert.assertTrue(bboxDBClient.getTuplesPerPage() >= -1);
		bboxDBClient.isPagingEnabled();
		bboxDBClient.setMaxInFlightCalls((short) 1000);
		Assert.assertEquals(1000, bboxDBClient.getMaxInFlightCalls());
		disconnect(bboxDBClient);
	}
	
	/**
	 * Should the packages be compressed or not
	 * @return
	 */
	protected boolean compressPackages() {
		return false;
	}
	
	/**
	 * Disconnect from server
	 * @param bboxDBClient
	 */
	protected void disconnect(final BBoxDB bboxDBClient) {
		bboxDBClient.disconnect();
		Assert.assertFalse(bboxDBClient.isConnected());
		Assert.assertEquals(0, bboxDBClient.getInFlightCalls());
	}
}
