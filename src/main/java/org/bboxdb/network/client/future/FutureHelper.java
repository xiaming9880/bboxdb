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
package org.bboxdb.network.client.future;

public class FutureHelper {

	/**
	 * Create and return an empty result future
	 * @return
	 */
	public static EmptyResultFuture getFailedEmptyResultFuture() {
		final EmptyResultFuture future = new EmptyResultFuture(1);
		future.setFailedState();
		future.fireCompleteEvent();
		return future;
	}
	
	/**
	 * Create and return an empty tuple list future
	 * @return
	 */
	public static TupleListFuture getFailedTupleListFuture() {
		final TupleListFuture future = new TupleListFuture(1);
		future.setFailedState();
		future.fireCompleteEvent();
		return future;
	}
}
