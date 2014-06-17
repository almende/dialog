package com.almende.util.twigmongo;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public interface MyMongoDBMixin {
	@JsonAnySetter
	void put(String key, Object value);
}
