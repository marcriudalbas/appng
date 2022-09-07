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
package org.appng.testapplication;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class TestEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	private @NotNull String name;

	private Integer integerValue;

	private Double doubleValue;

	private Boolean booleanValue;

	private @Transient TestEntity parent;
	private @Transient List<TestEntity> children;

	public TestEntity() {

	}

	public TestEntity(Integer id, String name, Integer integerValue, Double doubleValue, Boolean booleanValue) {
		super();
		this.id = id;
		this.name = name;
		this.integerValue = integerValue;
		this.doubleValue = doubleValue;
		this.booleanValue = booleanValue;
	}

}
