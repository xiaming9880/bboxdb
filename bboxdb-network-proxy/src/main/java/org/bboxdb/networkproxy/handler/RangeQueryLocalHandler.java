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
package org.bboxdb.networkproxy.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.bboxdb.commons.math.Hyperrectangle;
import org.bboxdb.distribution.membership.MembershipConnectionService;
import org.bboxdb.distribution.zookeeper.ZookeeperClientFactory;
import org.bboxdb.network.client.BBoxDBClient;
import org.bboxdb.network.client.BBoxDBCluster;
import org.bboxdb.network.client.BBoxDBConnection;
import org.bboxdb.network.client.future.TupleListFuture;
import org.bboxdb.networkproxy.ProxyConst;
import org.bboxdb.networkproxy.ProxyHelper;
import org.bboxdb.networkproxy.misc.TupleStringSerializer;
import org.bboxdb.storage.entity.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RangeQueryLocalHandler implements ProxyCommandHandler {

	/**
	 * The Logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(RangeQueryLocalHandler.class);

	@Override
	public void handleCommand(final BBoxDBCluster bboxdbCluster, final InputStream socketInputStream,
			final OutputStream socketOutputStream) throws IOException {

		final String table = ProxyHelper.readStringFromServer(socketInputStream);
		final String boundingBoxString = ProxyHelper.readStringFromServer(socketInputStream);
		final Hyperrectangle bbox = Hyperrectangle.fromString(boundingBoxString);

		logger.info("Got local call for table {} and bbox {}", table, bbox);

		try {
			final MembershipConnectionService connectionService = MembershipConnectionService.getInstance();
			final BBoxDBConnection localConnection = connectionService.getConnectionForInstance(ZookeeperClientFactory.getLocalInstanceName());
			
			// Connection is still used in the cluster, therefore the client is not closed
			@SuppressWarnings("resource")
			final BBoxDBClient localClient = new BBoxDBClient(localConnection);
			
			final TupleListFuture tupleResult = localClient.queryRectangle(table, bbox);
			tupleResult.waitForCompletion();

			for(final Tuple tuple : tupleResult) {
				socketOutputStream.write(ProxyConst.RESULT_FOLLOW);
				TupleStringSerializer.writeTuple(tuple, socketOutputStream);
			}

			socketOutputStream.write(ProxyConst.RESULT_OK);
		} catch(InterruptedException e) {
			logger.debug("Got interrupted exception while handling bboxdb call");
			Thread.currentThread().interrupt();
			socketOutputStream.write(ProxyConst.RESULT_FAILED);
		} catch (Exception e) {
			logger.error("Got exception while proessing bboxdb call", e);
			socketOutputStream.write(ProxyConst.RESULT_FAILED);
		}
	}
}
