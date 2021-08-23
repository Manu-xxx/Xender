package com.hedera.services.state.expiry.backgroundworker.buckets;

/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.hedera.services.state.expiry.backgroundworker.jobs.Job;
import com.hedera.services.state.expiry.backgroundworker.jobs.JobStatus;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Responsible for managing the expiration of lightweight entities - txn records and scheduled txns.
 */
public class ExpiryManager implements JobBucket {
	
	private List<Job> jobs;
	
	public ExpiryManager(List<Job> jobs) {
		this.jobs = jobs;
	}
	
	private void cleanupFinishedJobs() {
		this.jobs = jobs.stream().filter(e -> e.getStatus() != JobStatus.DONE).collect(Collectors.toList());
	}
	
	@Override
	public void doPreTransactionJobs(long now) {
		for (final var job : jobs) {
			job.execute(now);
		}
		cleanupFinishedJobs();
	}

	@Override
	public void doPostTransactionJobs(long now) {
		
	}
	
}
