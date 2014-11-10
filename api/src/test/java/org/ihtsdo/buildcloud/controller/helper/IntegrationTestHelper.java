package org.ihtsdo.buildcloud.controller.helper;

import com.jayway.jsonpath.JsonPath;

import org.apache.commons.codec.binary.Base64;
import org.ihtsdo.buildcloud.controller.AbstractControllerTest;
import org.ihtsdo.buildcloud.entity.helper.EntityHelper;
import org.ihtsdo.buildcloud.service.BuildService;
import org.ihtsdo.buildcloud.service.PackageService;
import org.ihtsdo.buildcloud.test.StreamTestUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class IntegrationTestHelper {

	public static final String PRODUCT_URL = "/centers/international/extensions/snomed_ct_international_edition/products/nlm_example_refset";

	private final MockMvc mockMvc;
	private String basicDigestHeaderValue;
	private String buildId;
	private String buildBusinessKey;
	private String packageBusinessKey;

	public static final String COMPLETION_STATUS = "completed";

	public IntegrationTestHelper(MockMvc mockMvc, String testName) {
		this.mockMvc = mockMvc;
		basicDigestHeaderValue = "NOT_YET_AUTHENTICATED"; // initial value only
		
		//We'll work with a build and package that are specific to this test
		this.buildBusinessKey = EntityHelper.formatAsBusinessKey(testName);
		this.packageBusinessKey = EntityHelper.formatAsBusinessKey(testName);
	}

	public void loginAsManager() throws Exception {
		MvcResult loginResult = mockMvc.perform(
				post("/login")
						.param("username", "manager")
						.param("password", "test123")
		)
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(content().contentType(AbstractControllerTest.APPLICATION_JSON_UTF8))
				.andExpect(jsonPath("$.authenticationToken", notNullValue()))
				.andReturn();

		String authenticationToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.authenticationToken");
		basicDigestHeaderValue = "Basic " + new String(Base64.encodeBase64((authenticationToken + ":").getBytes()));
	}

	public void createTestBuildStructure() throws Exception {
		
		this.buildId = findOrCreateBuild();
		findOrCreatePackage();
		
	}
	
	private String findOrCreateBuild() throws Exception {
		//Does the build already exist or do we need to create it?
		MvcResult buildResult = mockMvc.perform(
				get(PRODUCT_URL + "/builds/" +  this.buildBusinessKey)
						.header("Authorization", getBasicDigestHeaderValue())
		)
				.andDo(print())
				.andReturn();
		
		if (buildResult.getResponse().getStatus() == 404) {
			// Create Build
			 buildResult = mockMvc.perform(
					post(PRODUCT_URL + "/builds")
							.header("Authorization", getBasicDigestHeaderValue())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{ \"name\" : \"" + buildBusinessKey + "\" }")
			)
					.andDo(print())
					.andExpect(status().isCreated())
					.andExpect(content().contentType(AbstractControllerTest.APPLICATION_JSON_UTF8))
					.andReturn();
		}

		String createBuildResponseString = buildResult.getResponse().getContentAsString();
		int idStartIndex = createBuildResponseString.indexOf("\"id\" :") + 8;
		int idEndIndex = createBuildResponseString.indexOf("\"", idStartIndex);
		return createBuildResponseString.substring(idStartIndex, idEndIndex);
	}
	
	private void findOrCreatePackage() throws Exception {
		//Does the product already exist or do we need to create it?
		MvcResult packageResult = mockMvc.perform(
				get(PRODUCT_URL + "/builds/" + this.buildId + "/packages/"  + this.packageBusinessKey)
						.header("Authorization", getBasicDigestHeaderValue())
		)
				.andDo(print())
				.andReturn();
		
		if (packageResult.getResponse().getStatus() == 404) {
			// Create Package
			mockMvc.perform(
					post("/builds/" + buildId + "/packages")
							.header("Authorization", getBasicDigestHeaderValue())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{ \"name\" : \"" + this.packageBusinessKey + "\" }")
			)
					.andDo(print())
					//.andExpect((status().isOk()))  //Will return 409 if package already exists
					.andExpect(content().contentType(AbstractControllerTest.APPLICATION_JSON_UTF8));
		}
	}

	public void uploadDeltaInputFile(String deltaFileName, Class classpathResourceOwner) throws Exception {
		InputStream resourceAsStream = classpathResourceOwner.getResourceAsStream(deltaFileName);
		Assert.assertNotNull(deltaFileName + " stream is null.", resourceAsStream);
		MockMultipartFile deltaFile = new MockMultipartFile("file", deltaFileName, "text/plain", resourceAsStream);
		mockMvc.perform(
				fileUpload(getPackageUrl() + "/inputfiles")
						.file(deltaFile)
						.header("Authorization", getBasicDigestHeaderValue())
		)
				.andDo(print())
				.andExpect(status().isOk());
	}
	
	public void publishFile(String publishFileName, Class classpathResourceOwner, HttpStatus expectedStatus) throws Exception {
		MockMultipartFile publishFile = new MockMultipartFile("file", publishFileName, "text/plain", classpathResourceOwner.getResourceAsStream(publishFileName));
		mockMvc.perform(
				fileUpload(PRODUCT_URL + "/published")
						.file(publishFile)
						.header("Authorization", getBasicDigestHeaderValue())
		)
				.andDo(print())
				.andExpect(status().is(expectedStatus.value()));
	}

	public void deletePreviousTxtInputFiles() throws Exception {
		mockMvc.perform(
				request(HttpMethod.DELETE, getPackageUrl() + "/inputfiles/*.txt")
						.header("Authorization", getBasicDigestHeaderValue())
		)
				.andDo(print())
				.andExpect(status().isNoContent());
	}

	public void uploadManifest(String manifestFileName, Class classpathResourceOwner) throws Exception {
		MockMultipartFile manifestFile = new MockMultipartFile("file", manifestFileName, "text/plain", classpathResourceOwner.getResourceAsStream(manifestFileName));
		mockMvc.perform(
				fileUpload(getPackageUrl() + "/manifest")
						.file(manifestFile)
						.header("Authorization", getBasicDigestHeaderValue())
		)
				.andDo(print())
				.andExpect(status().isOk());
	}

	public void setEffectiveTime(String effectiveDate) throws Exception {
		String jsonContent = "{ " + jsonPair(BuildService.EFFECTIVE_TIME, getEffectiveDateWithSeparators(effectiveDate)) + " }";
		mockMvc.perform(
				request(HttpMethod.PATCH, "/builds/" + buildId)
						.header("Authorization", getBasicDigestHeaderValue())
						.contentType(MediaType.APPLICATION_JSON)
						.content(jsonContent)
		)
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(content().contentType(AbstractControllerTest.APPLICATION_JSON_UTF8));
	}

	private String getEffectiveDateWithSeparators(String effectiveDate) {
		return effectiveDate.substring(0, 4) + "-" + effectiveDate.substring(4, 6) + "-" + effectiveDate.substring(6, 8);
	}

	public void setFirstTimeRelease(boolean isFirstTime) throws Exception {
		setPackageProperty("{ " + jsonPair(PackageService.FIRST_TIME_RELEASE, Boolean.toString(isFirstTime)) + " }");
	}

	public void setCreateInferredRelationships(boolean isCreateInferredRelationships) throws Exception {
		setPackageProperty("{ " + jsonPair(PackageService.CREATE_INFERRED_RELATIONSHIPS, Boolean.toString(isCreateInferredRelationships)) + " }");
	}

	public void setWorkbenchDataFixesRequired(boolean isWorkbenchDataFixesRequired) throws Exception {
		setPackageProperty("{ " + jsonPair(PackageService.WORKBENCH_DATA_FIXES_REQUIRED, Boolean.toString(isWorkbenchDataFixesRequired)) + " }");
	}

	public void setJustPackage(boolean justPackage) throws Exception {
		setPackageProperty("{ " + jsonPair(PackageService.JUST_PACKAGE, Boolean.toString(justPackage)) + " }");
	}

	public void setPreviousPublishedPackage(String previousPublishedFile) throws Exception {
		setPackageProperty("{ " + jsonPair(PackageService.PREVIOUS_PUBLISHED_PACKAGE, previousPublishedFile) + " }");

	}

	public void setReadmeHeader(String readmeHeader) throws Exception {
		setPackageProperty("{ \"readmeHeader\" : \"" + readmeHeader + "\" }");
	}

	public void setReadmeEndDate(String readmeEndDate) throws Exception {
		setPackageProperty("{ \"readmeEndDate\" : \"" + readmeEndDate + "\" }");
	}

	public void setCustomRefsetCompositeKeys(String customRefsetCompositeKeys) throws Exception {
		setPackageProperty("{ \"" + PackageService.CUSTOM_REFSET_COMPOSITE_KEYS + "\" : \"" + customRefsetCompositeKeys + "\" }");
	}

	/*
		 * @return a string formatted for use as a JSON key/value pair eg 	"\"effectiveTime\" : \""+ effectiveDate + "\","
		 * with a trailing comma just in case you want more than one and json is OK with that if there's only one
		 */
	private String jsonPair(String key, String value) {
		return "  \"" + key + "\" : \"" + value + "\" ";
	}

	private void setPackageProperty(String jsonContent) throws Exception {
		mockMvc.perform(
				request(HttpMethod.PATCH, getPackageUrl())
						.header("Authorization", getBasicDigestHeaderValue())
						.contentType(MediaType.APPLICATION_JSON)
						.content(jsonContent)
		)
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(content().contentType(AbstractControllerTest.APPLICATION_JSON_UTF8));
	}

	public String createExecution() throws Exception {
		MvcResult createExecutionResult = mockMvc.perform(
				post("/builds/" + buildId + "/executions")
						.header("Authorization", getBasicDigestHeaderValue())
						.contentType(MediaType.APPLICATION_JSON)
		)
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(content().contentType(AbstractControllerTest.APPLICATION_JSON_UTF8))
				.andReturn();

		String executionId = JsonPath.read(createExecutionResult.getResponse().getContentAsString(), "$.id");
		return "/builds/" + buildId + "/executions/" + executionId;
	}

	public void triggerExecution(String executionURL) throws Exception {
		MvcResult triggerResult = mockMvc.perform(
				post(executionURL + "/trigger")
						.header("Authorization", getBasicDigestHeaderValue())
						.contentType(MediaType.APPLICATION_JSON)
		)
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(content().contentType(AbstractControllerTest.APPLICATION_JSON_UTF8))
				.andReturn();

		String outputFileListJson = triggerResult.getResponse().getContentAsString();
		JSONObject jsonObject = new JSONObject(outputFileListJson);
		JSONObject packageResults = jsonObject.getJSONObject("executionReport");
		Iterator packages = packageResults.keys();

		while (packages.hasNext()) {
			String packageName = (String) packages.next();
			JSONObject packageResult = packageResults.getJSONObject(packageName);
			String status = packageResult.getString("Progress Status");
			String message = packageResult.getString("Message");
			Assert.assertEquals("Package " + packageName + " bad status. Message: " + message, COMPLETION_STATUS, status);
		}
	}

	public void publishOutput(String executionURL) throws Exception {
		mockMvc.perform(
				post(executionURL + "/output/publish")
						.header("Authorization", getBasicDigestHeaderValue())
						.contentType(MediaType.APPLICATION_JSON)
		)
				.andDo(print())
				.andExpect(status().isOk());
	}

	public List<String> getZipEntryPaths(ZipFile zipFile) {
		List<String> entryPaths = new ArrayList<>();
		Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
		while (zipEntries.hasMoreElements()) {
			entryPaths.add(zipEntries.nextElement().getName());
		}
		return entryPaths;
	}

	public String getPreviousPublishedPackage() throws Exception {

		//Recover URL of published things from Product
		MvcResult productResult = mockMvc.perform(
				get(PRODUCT_URL)
						.header("Authorization", getBasicDigestHeaderValue())
						.contentType(MediaType.APPLICATION_JSON)
		)
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(content().contentType(AbstractControllerTest.APPLICATION_JSON_UTF8))
				.andReturn();

		String publishedURL = JsonPath.read(productResult.getResponse().getContentAsString(), "$.published_url");
		String expectedURL = "http://localhost:80/centers/international/extensions/snomed_ct_international_edition/products/nlm_example_refset/published";

		Assert.assertEquals(expectedURL, publishedURL);

		//Recover list of published packages
		MvcResult publishedResult = mockMvc.perform(
				get(publishedURL)
						.header("Authorization", getBasicDigestHeaderValue())
						.contentType(MediaType.APPLICATION_JSON)
		)
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(content().contentType(AbstractControllerTest.APPLICATION_JSON_UTF8))
				.andReturn();

		return JsonPath.read(publishedResult.getResponse().getContentAsString(), "$.publishedPackages[0]");
	}

	public ZipFile testZipNameAndEntryNames(String executionURL, String expectedZipFilename, String expectedZipEntries, Class classpathResourceOwner) throws Exception {
		MvcResult outputFileListResult = mockMvc.perform(
				get(executionURL + "/packages/" + this.packageBusinessKey + "/outputfiles")
						.header("Authorization", getBasicDigestHeaderValue())
						.contentType(MediaType.APPLICATION_JSON)
		)
				.andDo(print())
				.andExpect(status().isOk())
				.andReturn();

		String outputFileListJson = outputFileListResult.getResponse().getContentAsString();
		JSONArray jsonArray = new JSONArray(outputFileListJson);

		String zipFilePath = null;
		for (int a = 0; a < jsonArray.length(); a++) {
			JSONObject file = (JSONObject) jsonArray.get(a);
			String filePath = file.getString("id");
			if (filePath.endsWith(".zip")) {
				zipFilePath = filePath;
			}
		}

		Assert.assertEquals(expectedZipFilename, zipFilePath);

		ZipFile zipFile = new ZipFile(downloadToTempFile(executionURL, zipFilePath, classpathResourceOwner));
		List<String> entryPaths = getZipEntryPaths(zipFile);
		Assert.assertEquals("Zip entries expected.",
				expectedZipEntries,
				entryPaths.toString().replace(", ", "\n").replace("[", "").replace("]", ""));
		return zipFile;
	}

	private File downloadToTempFile(String executionURL, String zipFilePath, Class classpathResourceOwner) throws Exception {
		MvcResult outputFileResult = mockMvc.perform(
				get(executionURL + "/packages/" + this.packageBusinessKey + "/outputfiles/" + zipFilePath)
						.header("Authorization", getBasicDigestHeaderValue())
						.contentType(MediaType.APPLICATION_JSON)
		)
				.andExpect(status().isOk())
				.andReturn();

		Path tempFile = Files.createTempFile(classpathResourceOwner.getCanonicalName(), "output-file");
		try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile.toFile());
			 FileOutputStream tmp = new FileOutputStream("/tmp/zip.zip")) {
			byte[] contentAsByteArray = outputFileResult.getResponse().getContentAsByteArray();
			fileOutputStream.write(contentAsByteArray);
			tmp.write(contentAsByteArray);
		}
		return tempFile.toFile();
	}

	public void assertZipContents(String expectedOutputPackageName, ZipFile zipFile, Class classpathResourceOwner) throws IOException {
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			ZipEntry zipEntry = entries.nextElement();
			if (!zipEntry.isDirectory()) {
				String zipEntryName = getFileName(zipEntry);
				StreamTestUtils.assertStreamsEqualLineByLine(
						zipEntryName,
						classpathResourceOwner.getResourceAsStream(expectedOutputPackageName + "/" + zipEntryName),
						zipFile.getInputStream(zipEntry));
			}
		}
	}

	private String getFileName(ZipEntry zipEntry) {
		String name = zipEntry.getName();
		return name.substring(name.lastIndexOf("/") + 1);
	}

	public String getBasicDigestHeaderValue() {
		return basicDigestHeaderValue;
	}

	public String getPackageUrl() {
		return "/builds/" + buildId + "/packages/" + this.packageBusinessKey;
	}

}
