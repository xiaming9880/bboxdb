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
package org.bboxdb.network.query;

import java.util.List;

import org.bboxdb.commons.math.Hyperrectangle;
import org.bboxdb.network.query.transformation.TupleTransformation;

public class ContinuousTableQuery implements ContinuousQueryPlan {

	private final String streamTable;

	private final List<TupleTransformation> streamTransformation;

	private final Hyperrectangle compareRectangle;

	private boolean reportPositiveNegative;

	public ContinuousTableQuery(final String streamTable,
			final List<TupleTransformation> streamTransformation,
			final Hyperrectangle compareRectangle,
			final boolean reportPositiveNegative) {

				this.streamTable = streamTable;
				this.streamTransformation = streamTransformation;
				this.compareRectangle = compareRectangle;
				this.reportPositiveNegative = reportPositiveNegative;
	}

	@Override
	public String toJSON() {
		return null;
	}

}