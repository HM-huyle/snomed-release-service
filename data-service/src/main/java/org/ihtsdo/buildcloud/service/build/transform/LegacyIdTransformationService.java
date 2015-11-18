package org.ihtsdo.buildcloud.service.build.transform;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.ihtsdo.buildcloud.dao.BuildDAO;
import org.ihtsdo.buildcloud.entity.Build;
import org.ihtsdo.buildcloud.service.build.RF2Constants;
import org.ihtsdo.buildcloud.service.identifier.client.IdServiceRestClient;
import org.ihtsdo.buildcloud.service.identifier.client.SchemeIdType;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class LegacyIdTransformationService {

	private static final String BETA_PREFIX = "x";
	private static final String STATED_RELATIONSHIP_DELTA_FILE_PREFIX = "sct2_StatedRelationship_Delta_INT_";
	private static final String REFSET_SIMPLE_MAP_DELTA_FILE_PREFIX = "der2_sRefset_SimpleMapDelta_INT_";
	private static final String SNOMED_ID_REFSET_ID = "900000000000498005";
	private static final String CTV3_ID_REFSET_ID = "900000000000497000";
	private static final String TAB = "\t";
	private static final Logger LOGGER = LoggerFactory.getLogger(LegacyIdTransformationService.class);

	@Autowired
	private IdServiceRestClient idRestClient;
	@Autowired
	private UUIDGenerator uuidGenerator;
	@Autowired
	private BuildDAO buildDAO;
	
	public void transformLegacyIds(final CachedSctidFactory cachedSctidFactory, final Map<String, List<UUID>> moduleIdAndUuidMap, 
			final Build build) throws TransformationException {
		final List<UUID> newConceptUuids   = new ArrayList<>();
		for (final String moduleId : moduleIdAndUuidMap.keySet()) {
			newConceptUuids.addAll(moduleIdAndUuidMap.get(moduleId));
		}
		LOGGER.info("Total new concepts:" + newConceptUuids.size());
		//Generate CTV3 ID
		LOGGER.info("Start CTV3ID generation");
		Map<UUID, String> uuidCtv3IdMap = new HashMap<>();
		try {
			uuidCtv3IdMap = idRestClient.getOrCreateSchemeIds(newConceptUuids,SchemeIdType.CTV3ID, build.getUniqueId());
		} catch (RestClientException e) {
			throw new TransformationException("Failed to generate CTV3 IDs", e);
		}
		LOGGER.info("Created ctv3Ids for new concept ids found: " + uuidCtv3IdMap.size());
		//generate snomed id
		Map<UUID, String> uuidAndSnomedIdMap = new HashMap<>();
		try {
			uuidAndSnomedIdMap = idRestClient.getOrCreateSchemeIds(newConceptUuids, SchemeIdType.SNOMEDID, build.getUniqueId());
		} catch (RestClientException e) {
			throw new TransformationException("Failed to generate Snomed IDs", e);
		}
		LOGGER.info("Generated SnomedIds:" + uuidAndSnomedIdMap.keySet().size());
		final String effectiveDate = build.getConfiguration().getEffectiveTimeSnomedFormat();
		String fileNamePrefix = build.getConfiguration().isBetaRelease() ? BETA_PREFIX + REFSET_SIMPLE_MAP_DELTA_FILE_PREFIX  : REFSET_SIMPLE_MAP_DELTA_FILE_PREFIX;
		final String simpleRefsetMapDelta = fileNamePrefix + effectiveDate + RF2Constants.TXT_FILE_EXTENSION;
		final String orignalTransformedDelta = simpleRefsetMapDelta.replace(RF2Constants.TXT_FILE_EXTENSION, ".tmp");
		//can't append to existing file using S3 so need to rename existing transformed file then write again along with additional data.
		buildDAO.renameTransformedFile(build, simpleRefsetMapDelta, orignalTransformedDelta, false);
		try (
				final OutputStream outputStream = buildDAO.getTransformedFileOutputStream(build, simpleRefsetMapDelta).getOutputStream();
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, RF2Constants.UTF_8))) {
				final InputStream inputStream = buildDAO.getTransformedFileAsInputStream(build, orignalTransformedDelta);
				if (inputStream == null) {
					LOGGER.warn("No existing transformed file found for " + simpleRefsetMapDelta);
				} else {
						try (final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
							String line;
							while ((line = reader.readLine()) != null) {
								writer.write(line);
								writer.write(RF2Constants.LINE_ENDING);
							}
						} catch (final IOException e) {
							throw new TransformationException("Error occurred when reading original simple refset map delta transformed file:" + orignalTransformedDelta, e);
						}
				}
				//appending additional legacy data.
				if (!moduleIdAndUuidMap.isEmpty()) {
					for (final String moduleId : moduleIdAndUuidMap.keySet()) {
						String moduleIdSctId = moduleId;
						if (moduleId.contains("-")) {
							final Long mSctId = cachedSctidFactory.getSCTIDFromCache(moduleId);
							if (mSctId == null) {
								LOGGER.warn("No module id sctID found from cache for uuid: " + moduleId);
							} else {
								moduleIdSctId =  mSctId.toString();
							}
						}
						for (final UUID uuid : moduleIdAndUuidMap.get(moduleId)) {
							final Long sctId = cachedSctidFactory.getSCTIDFromCache(uuid.toString());
							writer.write(productSimpleRefsetMapDeltaLine(uuidGenerator.uuid(), effectiveDate, moduleIdSctId, CTV3_ID_REFSET_ID, sctId, uuidCtv3IdMap.get(uuid)));
							writer.write(RF2Constants.LINE_ENDING);
							final String snomedId = uuidAndSnomedIdMap.get(uuid);
							if (snomedId != null && !snomedId.equals("")) {
								writer.write(productSimpleRefsetMapDeltaLine(uuidGenerator.uuid(), effectiveDate, moduleIdSctId, SNOMED_ID_REFSET_ID, sctId, snomedId));
								writer.write(RF2Constants.LINE_ENDING);
							} else {
								LOGGER.warn("No SnomedID was generated for UUID:" + uuid);
							}
						}
					}
				}
		} catch (final IOException e) {
			throw new TransformationException("Error occurred when transforming " + simpleRefsetMapDelta, e);
		}
	}

	private String productSimpleRefsetMapDeltaLine(final String componentId, final String effectiveDate, final String moduleId, final String refsetId, final Long sctId, final String mapTarget) {
		final StringBuilder producter = new StringBuilder();
		producter.append(componentId);
		producter.append(TAB);
		producter.append(effectiveDate);
		producter.append(TAB);
		producter.append("1");
		producter.append(TAB);
		producter.append(moduleId);
		producter.append(TAB);
		producter.append(refsetId);
		producter.append(TAB);
		producter.append(sctId != null ? sctId.toString() : RF2Constants.NULL_STRING);
		producter.append(TAB);
		producter.append(mapTarget);
		return producter.toString();
	}
}
