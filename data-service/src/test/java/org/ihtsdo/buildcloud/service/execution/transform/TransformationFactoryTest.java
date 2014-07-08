package org.ihtsdo.buildcloud.service.execution.transform;
import java.util.List;

import org.ihtsdo.buildcloud.service.execution.database.DataType;
import org.ihtsdo.buildcloud.service.execution.database.FileRecognitionException;
import org.ihtsdo.buildcloud.service.execution.database.SchemaFactory;
import org.ihtsdo.buildcloud.service.execution.database.TableSchema;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
public class TransformationFactoryTest {

	private TransformationFactory transformationFactory;
	private TableSchema schemaBean;

	@Before
	public void setup() throws FileRecognitionException {
		transformationFactory = new TransformationFactory("01012014", new CachedSctidFactory(null, null, null, null), new RandomUUIDGenerator());
		schemaBean = new SchemaFactory().createSchemaBean("der2_iisssccRefset_ExtendedMapDelta_INT_20140131.txt");
		List<TableSchema.Field> fields = schemaBean.getFields();
		Assert.assertEquals(13, fields.size());
		Assert.assertEquals(DataType.INTEGER, fields.get(6).getType());
	}

	@Test
	public void testGetSteamingFileTransformation() throws Exception {

		StreamingFileTransformation transformation = transformationFactory.getSteamingFileTransformation(schemaBean);

		List<LineTransformation> lineTransformations = transformation.getLineTransformations();
		Assert.assertEquals(7, lineTransformations.size());
		int index = 0;
		assertTransform(UUIDTransformation.class, 0, lineTransformations.get(index++));
		assertTransform(ReplaceValueLineTransformation.class, 1, lineTransformations.get(index++));
		assertTransform(SCTIDTransformationFromCache.class, 3, lineTransformations.get(index++));
		assertTransform(SCTIDTransformationFromCache.class, 4, lineTransformations.get(index++));
		assertTransform(SCTIDTransformationFromCache.class, 5, lineTransformations.get(index++));
		assertTransform(SCTIDTransformationFromCache.class, 11, lineTransformations.get(index++));
		assertTransform(SCTIDTransformationFromCache.class, 12, lineTransformations.get(index++));

	}

	private void assertTransform(Class<? extends LineTransformation> expectedTransformationClass, int expectedColumnIndex,
			LineTransformation actualLineTransformation) {

		Assert.assertNotNull(actualLineTransformation);

		Assert.assertEquals("Transformation class.", expectedTransformationClass, actualLineTransformation.getClass());
		Assert.assertEquals("Column index.", expectedColumnIndex, actualLineTransformation.getColumnIndex());

	}
}