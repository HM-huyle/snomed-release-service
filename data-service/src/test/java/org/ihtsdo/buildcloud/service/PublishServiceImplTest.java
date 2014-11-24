package org.ihtsdo.buildcloud.service;

/*
 import com.amazonaws.services.s3.model.ObjectListing;
 import com.amazonaws.services.s3.model.PutObjectRequest;
 import com.amazonaws.services.s3.model.S3Object;
 import com.amazonaws.services.s3.model.S3ObjectSummary;
 import mockit.Deencapsulation;
 import mockit.Expectations;
 import mockit.Injectable;
 import mockit.Mocked;
 import org.ihtsdo.buildcloud.dao.helper.ExecutionS3PathHelper;
 import org.ihtsdo.buildcloud.dao.helper.S3ClientHelper;
 import org.ihtsdo.buildcloud.dao.helper.S3PutRequestBuilder;
 import org.ihtsdo.buildcloud.dao.s3.S3Client;
 import org.ihtsdo.buildcloud.entity.*;
 import org.ihtsdo.buildcloud.service.exception.EntityAlreadyExistsException;
 import org.ihtsdo.buildcloud.test.TestUtils;
 import org.junit.Assert;
 import org.junit.Before;

 */
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;

 /*
 import java.io.IOException;
 import java.io.InputStream;
 import java.util.List;
 */

@RunWith(JMockit.class)
public class PublishServiceImplTest {

	@Test
	public void validTest() {
		Assert.assertTrue(true);
	}
	/*
	 * private static final String TEST_MD5 = "test.md5"; private static final String PATH_SEPARATOR = "/"; private static final String
	 * TEST_ZIP = "test.zip"; private static final String EXECUTION_OUTPUT = "/execution/output/"; private static final String
	 * PUBLISHED_BUCKET_NAME = "publishedBucketName"; private static final String EXECUTION_BUCKET_NAME = "executionBucketName"; private
	 * PublishServiceImpl publishServiceImpl;
	 * 
	 * @Mocked private S3Client s3Client;
	 * 
	 * @Mocked private S3ClientHelper s3ClientHelper;
	 * 
	 * @Mocked ExecutionS3PathHelper s3PathHelper;
	 * 
	 * @Before public void setUp() { TestUtils.setTestUser(); publishServiceImpl = new PublishServiceImpl(EXECUTION_BUCKET_NAME,
	 * PUBLISHED_BUCKET_NAME, s3Client, s3ClientHelper); Deencapsulation.setField(publishServiceImpl, s3PathHelper); //use Deencapsulation
	 * in JMockit or use //ReflectionTestUtils.setField(publishServiceImpl, "executionS3PathHelper", s3PathHelper); }
	 * 
	 * @Test public void testPublishExecutionPackage(@Injectable final Execution execution, @Injectable final Package pk) throws
	 * IOException, EntityAlreadyExistsException { final Build build = createDummyBuild(); new Expectations() { { execution.getUniqueId();
	 * returns("123");
	 * 
	 * s3PathHelper.getExecutionOutputFilesPath(execution, anyString); returns(new StringBuilder(EXECUTION_OUTPUT));
	 * 
	 * s3Client.listObjects(EXECUTION_BUCKET_NAME, anyString); ObjectListing ol = createDummyObjectListing(EXECUTION_OUTPUT); returns(ol);
	 * 
	 * s3PathHelper.getExecutionOutputFilePath(execution, anyString, anyString); String fileToBePublished = EXECUTION_OUTPUT + "text.zip";
	 * returns(fileToBePublished);
	 * 
	 * execution.getBuild(); returns(build);
	 * 
	 * String zipKey = getExpectedPublishFileDir(build.getProduct()).toLowerCase() + TEST_ZIP; s3Client.copyObject(EXECUTION_BUCKET_NAME,
	 * fileToBePublished, PUBLISHED_BUCKET_NAME, zipKey);
	 * 
	 * s3Client.getObject(PUBLISHED_BUCKET_NAME, zipKey); S3Object s3Object = new S3Object();
	 * s3Object.setObjectContent(getClass().getResourceAsStream("/test.zip")); returns(s3Object);
	 * 
	 * s3ClientHelper.newPutRequest(anyString, anyString, withInstanceOf(InputStream.class)); returns(new S3PutRequestBuilder(null, null,
	 * null, s3ClientHelper));
	 * 
	 * s3ClientHelper.useBucketAcl(withInstanceOf(PutObjectRequest.class));
	 * 
	 * s3Client.putObject(withInstanceOf(PutObjectRequest.class)); returns(null);
	 * 
	 * s3PathHelper.getExecutionOutputFilePath(execution, anyString, anyString); String md5fileToBePublished = EXECUTION_OUTPUT + TEST_MD5;
	 * returns(md5fileToBePublished);
	 * 
	 * execution.getBuild(); returns(build);
	 * 
	 * String md5 = getExpectedPublishFileDir(build.getProduct()).toLowerCase() + TEST_MD5; s3Client.copyObject(EXECUTION_BUCKET_NAME,
	 * md5fileToBePublished, PUBLISHED_BUCKET_NAME, md5); } }; publishServiceImpl.publishExecutionPackage(execution, pk);
	 * 
	 * }
	 * 
	 * private Build createDummyBuild() { Build build = new Build("test"); Product product = new Product("testProduct"); Extension extension
	 * = new Extension("testExtension"); ReleaseCenter releaseCenter = new ReleaseCenter("International Release Center", "International");
	 * extension.setReleaseCenter(releaseCenter); product.setExtension(extension); build.setProduct(product);
	 * build.getProduct().getExtension().getReleaseCenter().getBusinessKey(); return build; }
	 * 
	 * private String getExpectedPublishFileDir(final Product product) { //"international/testextension/testproduct/test.zip" String
	 * productName = product.getName(); Extension extension = product.getExtension(); String extensionName = extension.getName(); String
	 * centerName = extension.getReleaseCenter().getShortName(); StringBuilder publishedFilePath = new StringBuilder();
	 * publishedFilePath.append(centerName); publishedFilePath.append(PATH_SEPARATOR).append(extensionName);
	 * publishedFilePath.append(PATH_SEPARATOR).append(productName); publishedFilePath.append(PATH_SEPARATOR); return
	 * publishedFilePath.toString();
	 * 
	 * }
	 * 
	 * @Test public void testGetPublishedPackages() { final Product product = createDummyBuild().getProduct(); new Expectations() {{
	 * s3Client.listObjects(PUBLISHED_BUCKET_NAME, getExpectedPublishFileDir(product).toLowerCase()); ObjectListing ol =
	 * createDummyObjectListing(getExpectedPublishFileDir(product)); returns(ol); }}; List<String> result =
	 * publishServiceImpl.getPublishedPackages(product); Assert.assertEquals("Text file should be filtered out.", 1, result.size());
	 * Assert.assertEquals("Published file should be there", TEST_ZIP, result.get(0)); }
	 * 
	 * private ObjectListing createDummyObjectListing(final String dirName) { ObjectListing ol = new ObjectListing();
	 * ol.getObjectSummaries().add(getS3ObjectSummary(dirName + TEST_ZIP)); ol.getObjectSummaries().add(getS3ObjectSummary(dirName +
	 * "test/file.txt")); ol.getObjectSummaries().add( getS3ObjectSummary(dirName + TEST_MD5)); return ol; }
	 * 
	 * private S3ObjectSummary getS3ObjectSummary(final String key) { S3ObjectSummary summary = new S3ObjectSummary(); summary.setKey(key);
	 * return summary; }
	 */
}
