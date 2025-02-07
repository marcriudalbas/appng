/*
 * Copyright 2011-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.appng.core.repository;

import java.util.List;

import org.appng.core.domain.JobRecord;
import org.appng.persistence.repository.SearchRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobRecordRepository extends SearchRepository<JobRecord, Integer> {

	List<JobRecord> findBySiteAndJobName(String site, String jobName);

	@Query("select distinct(j.application) from JobExecutionRecord j where j.site=?1 order by j.application")
	List<String> getDistinctApplications(String site);

	@Query("select distinct(j.jobName) from JobExecutionRecord j where j.site=?1 order by j.jobName")
	List<String> getDistinctJobNames(String site);

}
