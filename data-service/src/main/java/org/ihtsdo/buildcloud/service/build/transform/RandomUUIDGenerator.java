package org.ihtsdo.buildcloud.service.build.transform;

import java.util.UUID;

public class RandomUUIDGenerator implements UUIDGenerator {

	@Override
	public String uuid() {
		return UUID.randomUUID().toString().toLowerCase();
	}

}
