package org.ihtsdo.buildcloud.controller;

import org.ihtsdo.buildcloud.controller.helper.HypermediaGenerator;
import org.ihtsdo.buildcloud.service.ProductInputFileService;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/centers/{releaseCenterKey}/products/{productKey}")
@Api(value = "Input Files", position = 4)
public class InputFileController {

	@Autowired
	private ProductInputFileService productInputFileService;

	@Autowired
	private HypermediaGenerator hypermediaGenerator;

	private static final String FILENAME = "filename";

	private static final Logger LOGGER = LoggerFactory.getLogger(InputFileController.class);

	@RequestMapping(value = "/manifest", method = RequestMethod.POST)
	@ApiOperation( value = "Stores a manifest file",
		notes = "Stores or replaces a file identified as the manifest for the package specified in the URL" )
	@ResponseBody
	public ResponseEntity<Void> uploadManifestFile(@PathVariable final String releaseCenterKey,
			@PathVariable final String productKey, @RequestParam(value = "file") final MultipartFile file)
			throws IOException, ResourceNotFoundException {

		productInputFileService.putManifestFile(releaseCenterKey, productKey, file.getInputStream(), file.getOriginalFilename(), file.getSize());
		return new ResponseEntity<>(HttpStatus.CREATED);
	}

	@RequestMapping(value = "/manifest", method = RequestMethod.GET)
	@ApiOperation( value = "Returns a manifest file name",
		notes = "Returns a manifest file name for given release center and product" )
	@ResponseBody
	public Map<String, Object> getManifest(@PathVariable final String releaseCenterKey, @PathVariable final String productKey,
			final HttpServletRequest request) throws ResourceNotFoundException {
		String manifestFileName = productInputFileService.getManifestFileName(releaseCenterKey, productKey);
		Map<String, String> objectHashMap = new HashMap<>();
		if (manifestFileName != null) {
			objectHashMap.put(FILENAME, manifestFileName);
			return hypermediaGenerator.getEntityHypermedia(objectHashMap, true, request, "file");
		} else {
			return hypermediaGenerator.getEntityHypermedia(new HashMap<>(), true, request);
		}
	}

	@RequestMapping(value = "/manifest/file", produces = "application/xml", method = RequestMethod.GET)
	@ApiOperation( value = "Returns a specified manifest file",
		notes = "Returns the content of the manifest file as xml" )
	public void getManifestFile(@PathVariable final String releaseCenterKey, @PathVariable final String productKey,
			final HttpServletResponse response) throws ResourceNotFoundException {

		try (InputStream fileStream = productInputFileService.getManifestStream(releaseCenterKey, productKey)) {
			if (fileStream != null) {
				response.setContentType("application/xml");
				StreamUtils.copy(fileStream, response.getOutputStream());
			} else {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to stream manifest file from storage.", e);
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = "/inputfiles", method = RequestMethod.POST)
	@ApiOperation( value = "Store or Replace a file",
		notes = "Stores or replaces a file with its original name against the package specified in the URL" )
	@ResponseBody
	public ResponseEntity<Void> uploadInputFileFile(@PathVariable final String releaseCenterKey, @PathVariable final String productKey,
			@RequestParam(value = "file") final MultipartFile file) throws IOException, ResourceNotFoundException {
		String inputFileName = file.getOriginalFilename();
		LOGGER.debug("uploading input file:" + inputFileName);
		if (!Normalizer.isNormalized(inputFileName, Form.NFC)) {
			inputFileName = Normalizer.normalize(inputFileName, Form.NFC);
		}
		productInputFileService.putInputFile(releaseCenterKey, productKey, file.getInputStream(),inputFileName,file.getSize());
		return new ResponseEntity<>(HttpStatus.CREATED);
	}

	@RequestMapping(value = "/inputfiles", method = RequestMethod.GET)
	@ApiOperation( value = "Returns a list of file names",
		notes = "Returns a list of file names for the package specified in the URL" )
	@ResponseBody
	public List<Map<String, Object>> listInputFiles(@PathVariable final String releaseCenterKey, @PathVariable final String productKey,
			final HttpServletRequest request) throws ResourceNotFoundException {
		List<String> filePaths = productInputFileService.listInputFilePaths(releaseCenterKey, productKey);
		List<Map<String, String>> files = new ArrayList<>();
		for (String filePath : filePaths) {
			Map<String, String> file = new HashMap<>();
			file.put(ControllerConstants.ID, filePath);
			files.add(file);
		}
		return hypermediaGenerator.getEntityCollectionHypermedia(files, request);
	}

	@RequestMapping(value = "/inputfiles/{inputFileName:.*}", method = RequestMethod.GET)
	@ApiOperation( value = "Returns a specified file",
		notes = "Returns the content of the specified file." )
	public void getInputFileFile(@PathVariable final String releaseCenterKey, @PathVariable final String productKey,
			@PathVariable final String inputFileName,
			final HttpServletResponse response) throws ResourceNotFoundException {

		try (InputStream fileStream = productInputFileService.getFileInputStream(releaseCenterKey, productKey, inputFileName)) {
			if (fileStream != null) {
				StreamUtils.copy(fileStream, response.getOutputStream());
			} else {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to stream input file from storage.", e);
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	// Using Regex to match variable name here due to problems with .txt getting truncated
	// See http://stackoverflow.com/questions/16332092/spring-mvc-pathvariable-with-dot-is-getting-truncated
	@RequestMapping(value = "/inputfiles/{inputFileNamePattern:.+}", method = RequestMethod.DELETE)
	@ApiOperation( value = "Returns a specified file",
		notes = "Deletes the specified file, if found. "
			+ "Returns HTTP 404 if the file is not found for the package specified in the URL" )
	public ResponseEntity<Object> deleteInputFile(@PathVariable final String releaseCenterKey, @PathVariable final String productKey,
			@PathVariable final String inputFileNamePattern) throws IOException, ResourceNotFoundException {

		// Are we working with a file name or a pattern?
		if (inputFileNamePattern.contains("*")) {
			productInputFileService.deleteFilesByPattern(releaseCenterKey, productKey, inputFileNamePattern);
		} else {
			productInputFileService.deleteFile(releaseCenterKey, productKey, inputFileNamePattern);
		}
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

}
