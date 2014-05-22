package org.ihtsdo.buildcloud.service;

import org.ihtsdo.buildcloud.entity.Execution;
import org.ihtsdo.buildcloud.entity.User;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface ExecutionService {

	Execution create(String buildCompositeKey, User authenticatedUser) throws IOException;

	List<Execution> findAll(String buildCompositeKey, User authenticatedUser);

	Execution find(String buildCompositeKey, String executionId, User authenticatedUser);

	String loadConfiguration(String buildCompositeKey, String executionId, User authenticatedUser) throws IOException;

	Execution triggerBuild(String buildCompositeKey, String executionId, User authenticatedUser) throws IOException;

	void streamBuildScriptsZip(String buildCompositeKey, String executionId, User authenticatedUser, OutputStream outputStream) throws IOException;

	void saveOutputFile(String buildCompositeKey, String executionId, String filePath, InputStream inputStream, Long size, User authenticatedUser);

	void updateStatus(String buildCompositeKey, String executionId, String status, User authenticatedUser);

	InputStream getOutputFile(String buildCompositeKey, String executionId, String filePath, User authenticatedUser);

}