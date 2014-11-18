package org.ihtsdo.buildcloud.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.buildcloud.dao.InputFileDAO;
import org.ihtsdo.buildcloud.dao.PackageDAO;
import org.ihtsdo.buildcloud.dao.helper.ExecutionS3PathHelper;
import org.ihtsdo.buildcloud.dao.helper.FileHelper;
import org.ihtsdo.buildcloud.dao.helper.S3ClientHelper;
import org.ihtsdo.buildcloud.dao.s3.S3Client;
import org.ihtsdo.buildcloud.entity.Package;
import org.ihtsdo.buildcloud.entity.User;
import org.ihtsdo.buildcloud.service.exception.ResourceNotFoundException;
import org.ihtsdo.buildcloud.service.execution.RF2Constants;
import org.ihtsdo.buildcloud.service.file.FileUtils;
import org.ihtsdo.buildcloud.service.helper.CompositeKeyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InputFileServiceImpl implements InputFileService {

	public static final Logger LOGGER = LoggerFactory.getLogger(InputFileServiceImpl.class);

	private final FileHelper fileHelper;

	@Autowired
	private PackageDAO packageDAO;

	@Autowired
	private InputFileDAO dao;

	@Autowired
	private ExecutionS3PathHelper s3PathHelper;

	@Autowired
	public InputFileServiceImpl(final String executionBucketName, final S3Client s3Client, final S3ClientHelper s3ClientHelper) {
		fileHelper = new FileHelper(executionBucketName, s3Client, s3ClientHelper);
	}

	@Override
	public void putManifestFile(final String buildCompositeKey, final String packageBusinessKey, final InputStream inputStream, final String originalFilename, final long fileSize, final User authenticatedUser) throws ResourceNotFoundException {
		Package pkg = getPackage(buildCompositeKey, packageBusinessKey, authenticatedUser);
		dao.putManifestFile(pkg, inputStream, originalFilename, fileSize);
	}

	@Override
	public String getManifestFileName(final String buildCompositeKey, final String packageBusinessKey, final User authenticatedUser) throws ResourceNotFoundException {
		Package aPackage = getPackage(buildCompositeKey, packageBusinessKey, authenticatedUser);
		StringBuffer manifestDirectoryPathSB = s3PathHelper.getPackageManifestDirectoryPath(aPackage);
		List<String> files = fileHelper.listFiles(manifestDirectoryPathSB.toString());
		if (!files.isEmpty()) {
			return files.iterator().next();
		} else {
			return null;
		}
	}

	@Override
	public InputStream getManifestStream(final String buildCompositeKey, final String packageBusinessKey, final User authenticatedUser) throws ResourceNotFoundException {
		Package aPackage = getPackage(buildCompositeKey, packageBusinessKey, authenticatedUser);
		return dao.getManifestStream(aPackage);
	}

	@Override
	public void putInputFile(final String buildCompositeKey, final String packageBusinessKey, final InputStream inputStream, final String filename, final long fileSize, final User authenticatedUser) throws ResourceNotFoundException, IOException {
		Package aPackage = getPackage(buildCompositeKey, packageBusinessKey, authenticatedUser);

		if (filename.endsWith(RF2Constants.ZIP_FILE_EXTENSION)) {
			Path tempFile = Files.createTempFile(getClass().getCanonicalName(), ".zip");
			try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
				ZipEntry entry;
				while ((entry = zipInputStream.getNextEntry()) != null) {
					String path = entry.getName();
					String entryFilename = FileUtils.getFilenameFromPath(path);
					String pathPath = s3PathHelper.getPackageInputFilePath(aPackage, entryFilename);
					Files.copy(zipInputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
					try (FileInputStream tempFileInputStream = new FileInputStream(tempFile.toFile())) {
						fileHelper.putFile(tempFileInputStream, tempFile.toFile().length(), pathPath);
					}
				}
			} finally {
				if (!tempFile.toFile().delete()) {
					LOGGER.warn("Failed to delete temp file {}", tempFile.toFile().getAbsolutePath());
				}
			}
		} else {
			String pathPath = s3PathHelper.getPackageInputFilePath(aPackage, filename);
			fileHelper.putFile(inputStream, fileSize, pathPath);
		}
	}

	@Override
	public InputStream getFileInputStream(final String buildCompositeKey, final String packageBusinessKey, final String filename, final User authenticatedUser) throws ResourceNotFoundException {
		Package aPackage = getPackage(buildCompositeKey, packageBusinessKey, authenticatedUser);
		return getFileInputStream(aPackage, filename);
	}

	@Override
	public List<String> listInputFilePaths(final String buildCompositeKey, final String packageBusinessKey, final User authenticatedUser) throws ResourceNotFoundException {
		Package aPackage = getPackage(buildCompositeKey, packageBusinessKey, authenticatedUser);
		return dao.listInputFilePaths(aPackage);
	}

	@Override
	public void deleteFile(final String buildCompositeKey, final String packageBusinessKey, final String filename, final User authenticatedUser) throws ResourceNotFoundException {
		Package aPackage = getPackage(buildCompositeKey, packageBusinessKey, authenticatedUser);
		String filePath = s3PathHelper.getPackageInputFilePath(aPackage, filename);
		fileHelper.deleteFile(filePath);
	}

	@Override
	public void deleteFilesByPattern(final String buildCompositeKey,
			final String packageBusinessKey, final String inputFileNamePattern,
			final User authenticatedUser) throws ResourceNotFoundException {

		List<String> filesFound = listInputFilePaths(buildCompositeKey, packageBusinessKey, authenticatedUser);

		//Need to convert a standard file wildcard to a regex pattern
		String regexPattern = inputFileNamePattern.replace(".", "\\.").replace("*", ".*");
		Pattern pattern = Pattern.compile(regexPattern);
		for (String inputFileName : filesFound) {
			if (pattern.matcher(inputFileName).matches()) {
				deleteFile(buildCompositeKey, packageBusinessKey, inputFileName, authenticatedUser);
			}
		}
	}

	private InputStream getFileInputStream(final Package pkg, final String filename) {
		String filePath = s3PathHelper.getPackageInputFilePath(pkg, filename);
		return fileHelper.getFileStream(filePath);
	}

	private Package getPackage(final String buildCompositeKey, final String packageBusinessKey, final User authenticatedUser) throws ResourceNotFoundException {
		Long buildId = CompositeKeyHelper.getId(buildCompositeKey);
		if (buildId == null) {
			throw new ResourceNotFoundException("Unable to find build: " + buildCompositeKey);
		}
		Package aPackage = packageDAO.find(CompositeKeyHelper.getId(buildCompositeKey), packageBusinessKey, authenticatedUser);
		if (aPackage == null) {
			String item = CompositeKeyHelper.getPath(buildCompositeKey, packageBusinessKey);
			throw new ResourceNotFoundException("Unable to find package: " + item);
		}
		return aPackage;
	}

}
