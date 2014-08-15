package org.ihtsdo.buildcloud.service.execution;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import org.ihtsdo.buildcloud.dao.ExecutionDAO;
import org.ihtsdo.buildcloud.dao.io.AsyncPipedStreamBean;
import org.ihtsdo.buildcloud.entity.Execution;
import org.ihtsdo.buildcloud.entity.Package;
import org.ihtsdo.buildcloud.entity.Product;
import org.ihtsdo.buildcloud.service.execution.database.RF2TableDAO;
import org.ihtsdo.buildcloud.service.execution.database.RF2TableResults;
import org.ihtsdo.buildcloud.service.execution.database.Rf2FileWriter;
import org.ihtsdo.buildcloud.service.execution.database.map.RF2TableDAOTreeMapImpl;
import org.ihtsdo.buildcloud.service.helper.StatTimer;
import org.ihtsdo.snomed.util.rf2.schema.TableSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class Rf2FileExportService {

	private final Execution execution;
	private final Package pkg;
	private final Product product;
	private final ExecutionDAO executionDao;
	private final int maxRetries;
	private static final Logger LOGGER = LoggerFactory.getLogger(Rf2FileExportService.class);

	public Rf2FileExportService(final Execution execution, final Package pkg, ExecutionDAO dao, int maxRetries) {
		this.execution = execution;
		this.pkg = pkg;
		product = pkg.getBuild().getProduct();
		executionDao = dao;
		this.maxRetries = maxRetries;
	}

	public final void generateReleaseFiles() {
		boolean firstTimeRelease = pkg.isFirstTimeRelease();
		String effectiveTime = pkg.getBuild().getEffectiveTimeSnomedFormat();
		List<String> transformedFiles = getTransformedDeltaFiles();
		for (String thisFile : transformedFiles) {
			int failureCount = 0;
			boolean success = false;
			do {
				try {
					generateReleaseFile(thisFile, firstTimeRelease, effectiveTime);
					success = true;
				} catch (ReleaseFileGenerationException e) {
					failureCount++;
					// Is this an error that it's worth retrying eg root cause IOException or AWS Related?
					Throwable cause = e.getCause();
					if (failureCount > maxRetries) {
						throw new ReleaseFileGenerationException("Maximum failure recount of " + maxRetries + " exceeeded. Last error: "
								+ e.getMessage(), e);
					} else if (!isNetworkRelated(cause)) {
						// If this isn't something we think we might recover from by retrying, then just re-throw the existing error without
						// modification
						throw e;
					} else {
						LOGGER.warn("Failure while processing {} due to: {}. Retrying ({})...", thisFile, e.getMessage(), failureCount);
					}
				}
			} while (!success);
		}
	}

	private boolean isNetworkRelated(Throwable cause) {
		boolean isNetworkRelated = false;
		if (cause != null &&
				(cause instanceof IOException || cause instanceof AmazonServiceException || cause instanceof AmazonClientException)) {
			isNetworkRelated = true;
		}
		return isNetworkRelated;
	}

	private void generateReleaseFile(String transformedDeltaDataFile, boolean firstTimeRelease, String effectiveTime) {
	    	LOGGER.info("Generating release file using {}, isFirstRelease={}", transformedDeltaDataFile, firstTimeRelease);
		StatTimer timer = new StatTimer(getClass());
		RF2TableDAO rf2TableDAO = null;
		TableSchema tableSchema = null;
		try {
			// Create table containing transformed input delta
			LOGGER.debug("Creating table for {}", transformedDeltaDataFile);
			InputStream transformedDeltaInputStream = executionDao.getTransformedFileAsInputStream(execution,
					pkg.getBusinessKey(), transformedDeltaDataFile);

			rf2TableDAO = new RF2TableDAOTreeMapImpl();
			timer.split();
			tableSchema = rf2TableDAO.createTable(transformedDeltaDataFile, transformedDeltaInputStream);

			String currentFullFileName = transformedDeltaDataFile.replace(RF2Constants.DELTA, RF2Constants.FULL);
			String previousPublishedPackage = null;
			if (!firstTimeRelease) {
				// Find previous published package
				previousPublishedPackage = pkg.getPreviousPublishedPackage();
				InputStream previousFullFileStream = getPreviousFullStream(previousPublishedPackage, currentFullFileName);

				// Workbench workaround - use full file to discard invalid delta entries
				// See interface javadoc for more info.
				rf2TableDAO.discardAlreadyPublishedDeltaStates(previousFullFileStream, currentFullFileName, effectiveTime);
			}

			LOGGER.debug("Start: Exporting delta file for {}", tableSchema.getTableName());
			timer.setTargetEntity(tableSchema.getTableName());
			timer.logTimeTaken("Create table");

			// Export ordered Delta file
			Rf2FileWriter rf2FileWriter = new Rf2FileWriter();
			AsyncPipedStreamBean deltaFileAsyncPipe = executionDao
					.getOutputFileOutputStream(execution, pkg.getBusinessKey(), transformedDeltaDataFile);

			RF2TableResults deltaResultSet;
			timer.split();
			if (firstTimeRelease) {
				deltaResultSet = rf2TableDAO.selectNone(tableSchema);
				timer.logTimeTaken("Select none");
			} else {
				deltaResultSet = rf2TableDAO.selectAllOrdered(tableSchema);
				timer.logTimeTaken("Select all ordered");
			}

			timer.split();
			rf2FileWriter.exportDelta(deltaResultSet, tableSchema, deltaFileAsyncPipe.getOutputStream());
			LOGGER.debug("Completed processing delta file for {}, waiting for network", tableSchema.getTableName());
			timer.logTimeTaken("Export delta processing");
			deltaFileAsyncPipe.waitForFinish();
			LOGGER.debug("Finish: Exporting delta file for {}", tableSchema.getTableName());

			if (!firstTimeRelease) {
				InputStream previousFullFileStream = getPreviousFullStream(previousPublishedPackage, currentFullFileName);

				// Append transformed previous full file
				LOGGER.debug("Start: Insert previous release data into table {}", tableSchema.getTableName());
				timer.split();
				rf2TableDAO.appendData(tableSchema, previousFullFileStream);
				timer.logTimeTaken("Insert previous release data");
				LOGGER.debug("Finish: Insert previous release data into table {}", tableSchema.getTableName());
			}

			// Export Full and Snapshot files
			AsyncPipedStreamBean fullFileAsyncPipe = executionDao
					.getOutputFileOutputStream(execution, pkg.getBusinessKey(), currentFullFileName);
			String snapshotOutputFilePath = transformedDeltaDataFile.replace(RF2Constants.DELTA, RF2Constants.SNAPSHOT);
			AsyncPipedStreamBean snapshotAsyncPipe = executionDao
					.getOutputFileOutputStream(execution, pkg.getBusinessKey(), snapshotOutputFilePath);

			timer.split();
			RF2TableResults fullResultSet = rf2TableDAO.selectAllOrdered(tableSchema);
			timer.logTimeTaken("selectAllOrdered");

			rf2FileWriter.exportFullAndSnapshot(fullResultSet, tableSchema,
					pkg.getBuild().getEffectiveTime(), fullFileAsyncPipe.getOutputStream(),
					snapshotAsyncPipe.getOutputStream());
			LOGGER.debug("Completed processing full and snapshot files for {}, waiting for network.", tableSchema.getTableName());
			fullFileAsyncPipe.waitForFinish();
			snapshotAsyncPipe.waitForFinish();
		} catch (Exception e) {
			String errorMsg = "Failed to generate subsequent full and snapshort release files due to: " + e.getMessage();
			throw new ReleaseFileGenerationException(errorMsg, e);
		} finally {
			// Clean up time
			if (rf2TableDAO != null) {
				try {
					rf2TableDAO.closeConnection();
				} catch (Exception e) {
					LOGGER.error("Failure while trying to clean up after {}", tableSchema.getTableName(), e);
				}
			}
		}
	}

	private InputStream getPreviousFullStream(String previousPublishedPackage, String currentFullFileName) throws IOException {
		InputStream previousFullFileStream = executionDao.getPublishedFileArchiveEntry(product, currentFullFileName, previousPublishedPackage);
		if (previousFullFileStream == null) {
			throw new RuntimeException("No full file equivalent of:  "
					+ currentFullFileName
					+ " found in prevous published package:" + previousPublishedPackage);
		}
		return previousFullFileStream;
	}

	/**
	 * @return the transformed delta file name exception if not found.
	 */
	protected List<String> getTransformedDeltaFiles() {
		String businessKey = pkg.getBusinessKey();
		List<String> transformedFilePaths = executionDao.listTransformedFilePaths(execution, businessKey);
		List<String> validFiles = new ArrayList<>();
		if (transformedFilePaths.size() < 1) {
			throw new RuntimeException(
					"Failed to find any transformed files to convert to output delta files.");
		}

		for (String fileName : transformedFilePaths) {
			if (fileName.endsWith(RF2Constants.TXT_FILE_EXTENSION)
					&& fileName.contains(RF2Constants.DELTA)) {
				validFiles.add(fileName);
			}
		}
		if (validFiles.size() == 0) {
			throw new ReleaseFileGenerationException(
					"Failed to find any files of type *Delta*.txt transformed in package:"
							+ businessKey);
		}
		return validFiles;
	}

}
