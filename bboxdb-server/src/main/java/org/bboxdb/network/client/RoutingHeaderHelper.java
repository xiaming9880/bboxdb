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
package org.bboxdb.network.client;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bboxdb.distribution.DistributionRegion;
import org.bboxdb.distribution.partitioner.SpacePartitioner;
import org.bboxdb.distribution.partitioner.SpacePartitionerCache;
import org.bboxdb.distribution.zookeeper.ZookeeperException;
import org.bboxdb.network.routing.RoutingHeader;
import org.bboxdb.network.routing.RoutingHop;
import org.bboxdb.network.routing.RoutingHopHelper;
import org.bboxdb.storage.entity.BoundingBox;
import org.bboxdb.storage.entity.TupleStoreName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoutingHeaderHelper {
	
	/**
	 * The Logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(RoutingHeaderHelper.class);

	/**
	 * Get the routing header for the local system
	 * @param table
	 * @param serverAddress 
	 * @param tuple
	 * @throws ZookeeperException
	 * @throws BBoxDBException
	 * @throws InterruptedException
	 */
	public static RoutingHeader getRoutingHeaderForLocalSystem(final String table, final BoundingBox boundingBox, 
			final boolean allowEmptyHop, final InetSocketAddress serverAddress) 
			throws ZookeeperException, BBoxDBException, InterruptedException {

		final TupleStoreName ssTableName = new TupleStoreName(table);

		final SpacePartitioner spacepartitioner = SpacePartitionerCache.getSpaceParitionerForTableName(
				ssTableName);

		final DistributionRegion distributionRegion = spacepartitioner.getRootNode();

		final List<RoutingHop> hops = RoutingHopHelper.getRoutingHopsForWrite(boundingBox, 
				distributionRegion);

		if(hops.isEmpty()) {
			if(! allowEmptyHop) {
				throw new BBoxDBException("Got empty result list when query for write: " + boundingBox);
			}
			
			return new RoutingHeader((short) 0, new ArrayList<>());
		}
		
		// Filter the local hop
		final List<RoutingHop> connectionHop = hops.stream()
				.filter(r -> r.getDistributedInstance().getInetSocketAddress().equals(serverAddress))
				.collect(Collectors.toList());

		if(! allowEmptyHop && connectionHop.isEmpty()) {
			throw new BBoxDBException("Unable to find host " + serverAddress + " in global routing list: " 
					+ hops);
		}

		return new RoutingHeader((short) 0, connectionHop);
	}

	/**
	 * Get local routing header without exception
	 * @param table
	 * @param boundingBox
	 * @param allowEmptyHop
	 * @return
	 */
	public static RoutingHeader getRoutingHeaderForLocalSystemNE(final String table, final BoundingBox boundingBox, 
			final boolean allowEmptyHop, final InetSocketAddress serverAddress) {
		
		try {
			return getRoutingHeaderForLocalSystem(table, boundingBox, allowEmptyHop, serverAddress);
		} catch (ZookeeperException | BBoxDBException | InterruptedException e) {
			logger.error("Got exception", e);
			return null;
		}
	}
	
}