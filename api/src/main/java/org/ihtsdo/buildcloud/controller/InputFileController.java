package org.ihtsdo.buildcloud.controller;

import org.ihtsdo.buildcloud.controller.helper.HypermediaGenerator;
import org.ihtsdo.buildcloud.entity.*;
import org.ihtsdo.buildcloud.security.SecurityHelper;
import org.ihtsdo.buildcloud.service.InputFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/builds/{buildCompositeKey}/packages/{packageBusinessKey}/input-files")
public class InputFileController {

	@Autowired
	private InputFileService inputFileService;

	@Autowired
	private HypermediaGenerator hypermediaGenerator;

	private static final String[] INPUT_FILE_LINKS = {};

	@RequestMapping
	@ResponseBody
	public List<Map<String, Object>> getInputFiles(@PathVariable String buildCompositeKey,
												   @PathVariable String packageBusinessKey, HttpServletRequest request) {
		String authenticatedId = SecurityHelper.getSubject();
		Set<InputFile> inputFiles = inputFileService.findAll(buildCompositeKey, packageBusinessKey, authenticatedId);
		return hypermediaGenerator.getEntityCollectionHypermedia(inputFiles, request, INPUT_FILE_LINKS);
	}

	@RequestMapping("/{inputFileBusinessKey}")
	@ResponseBody
	public Map getInputFile(@PathVariable String buildCompositeKey, @PathVariable String packageBusinessKey,
							@PathVariable String inputFileBusinessKey, HttpServletRequest request) {
		String authenticatedId = SecurityHelper.getSubject();
		InputFile inputFile = inputFileService.find(buildCompositeKey, packageBusinessKey, inputFileBusinessKey, authenticatedId);
		return hypermediaGenerator.getEntityHypermedia(inputFile, request, INPUT_FILE_LINKS);
	}


}