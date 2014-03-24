package org.ihtsdo.buildcloud.controller;

import org.ihtsdo.buildcloud.controller.helper.HypermediaGenerator;
import org.ihtsdo.buildcloud.controller.helper.LinkPath;
import org.ihtsdo.buildcloud.entity.Build;
import org.ihtsdo.buildcloud.security.SecurityHelper;
import org.ihtsdo.buildcloud.service.BuildService;
import org.ihtsdo.buildcloud.service.helper.FilterOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/builds")
public class BuildController {

	@Autowired
	private BuildService buildService;

	@Autowired
	private HypermediaGenerator hypermediaGenerator;

	public static final LinkPath[] BUILD_LINKS = { new LinkPath("packages"), 
												   new LinkPath ("executions")};

	private static final Logger LOGGER = LoggerFactory.getLogger(BuildController.class);

	@RequestMapping
	@ResponseBody
	public List<Map<String, Object>> getBuilds( @RequestParam(value="includeRemoved", required=false) String includeRemovedStr,
												@RequestParam(value="starred", required=false) String starredStr,
												HttpServletRequest request) {
		
		EnumSet <FilterOption> filterOptions = EnumSet.noneOf(FilterOption.class);
		if (Boolean.parseBoolean(includeRemovedStr)) filterOptions.add(FilterOption.INCLUDE_REMOVED);
		if (Boolean.parseBoolean(starredStr)) filterOptions.add(FilterOption.STARRED_ONLY);
		
		String authenticatedId = SecurityHelper.getSubject();
		List<Build> builds = buildService.findAll(filterOptions, authenticatedId);
		return hypermediaGenerator.getEntityCollectionHypermedia(builds, request, BUILD_LINKS);
	}

	@RequestMapping("/{buildCompositeKey}")
	@ResponseBody
	public Map getBuild(@PathVariable String buildCompositeKey, HttpServletRequest request) {
		String authenticatedId = SecurityHelper.getSubject();
		Build build = buildService.find(buildCompositeKey, authenticatedId);

		return hypermediaGenerator.getEntityHypermedia(build, request, BUILD_LINKS);
	}
	
}
