package com.hedera.services.fees.calculation.file;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.file.queries.GetFileContentsResourceUsage;
import com.hedera.services.fees.calculation.file.queries.GetFileInfoResourceUsage;
import com.hedera.services.fees.calculation.file.txns.FileAppendResourceUsage;
import com.hedera.services.fees.calculation.file.txns.FileCreateResourceUsage;
import com.hedera.services.fees.calculation.file.txns.FileDeleteResourceUsage;
import com.hedera.services.fees.calculation.file.txns.FileUpdateResourceUsage;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;

import java.util.List;
import java.util.Set;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;

@Module
public abstract class FileFeesModule {
	@Provides
	@ElementsIntoSet
	public static Set<QueryResourceUsageEstimator> provideFileQueryEstimators(
			GetFileInfoResourceUsage getFileInfoResourceUsage,
			GetFileContentsResourceUsage getFileContentsResourceUsage
	) {
		return Set.of(getFileInfoResourceUsage, getFileContentsResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(FileCreate)
	public static List<TxnResourceUsageEstimator> provideFileCreateEstimator(
			FileCreateResourceUsage fileCreateResourceUsage
	) {
		return List.of(fileCreateResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(FileDelete)
	public static List<TxnResourceUsageEstimator> provideFileDeleteEstimator(
			FileDeleteResourceUsage fileDeleteResourceUsage
	) {
		return List.of(fileDeleteResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(FileUpdate)
	public static List<TxnResourceUsageEstimator> provideFileUpdateEstimator(
			FileUpdateResourceUsage fileUpdateResourceUsage
	) {
		return List.of(fileUpdateResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(FileAppend)
	public static List<TxnResourceUsageEstimator> provideFileAppendEstimator(
			FileAppendResourceUsage fileAppendResourceUsage
	) {
		return List.of(fileAppendResourceUsage);
	}
}
