package org.ihtsdo.buildcloud.controller;

import org.ihtsdo.buildcloud.controller.helper.HypermediaGenerator;
import org.ihtsdo.buildcloud.controller.helper.LinkPath;
import org.ihtsdo.buildcloud.entity.ReleaseCentre;
import org.ihtsdo.buildcloud.security.SecurityHelper;
import org.ihtsdo.buildcloud.service.ReleaseCentreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/centres")
public class ReleaseCentreController {

	@Autowired
	private ReleaseCentreService releaseCentreService;

	@Autowired
	private HypermediaGenerator hypermediaGenerator;

	private static final LinkPath[] RELEASE_CENTRE_LINKS = {new LinkPath ("extensions")};

	@RequestMapping
	@ResponseBody
	public List<Map<String, Object>> getReleaseCentres(HttpServletRequest request) {
		String authenticatedId = SecurityHelper.getSubject();
		List<ReleaseCentre> centres = releaseCentreService.findAll(authenticatedId);
		return hypermediaGenerator.getEntityCollectionHypermedia(centres, request, RELEASE_CENTRE_LINKS);
	}

	@RequestMapping(value = "", method = RequestMethod.POST, consumes = MediaType.ALL_VALUE)
	public ResponseEntity<Map> createReleaseCentre(@RequestBody(required = false) Map<String, String> json,
												   HttpServletRequest request) throws IOException {
		String name = json.get("name");
		String shortName = json.get("shortName");

		String authenticatedId = SecurityHelper.getSubject();
		ReleaseCentre centre = releaseCentreService.create(name, shortName, authenticatedId);
		Map<String, Object> entityHypermedia = hypermediaGenerator.getEntityHypermediaJustCreated(centre, request, RELEASE_CENTRE_LINKS);
		return new ResponseEntity<Map>(entityHypermedia, HttpStatus.CREATED);
	}

	@RequestMapping(value = "/{releaseCentreBusinessKey}", method = RequestMethod.PUT, consumes = MediaType.ALL_VALUE)
	@ResponseBody
	public Map updateReleaseCentre(@PathVariable String releaseCentreBusinessKey,
												   @RequestBody(required = false) Map<String, String> json,
												   HttpServletRequest request) throws IOException {

		String authenticatedId = SecurityHelper.getSubject();
		ReleaseCentre centre = releaseCentreService.find(releaseCentreBusinessKey, authenticatedId);
		centre.setName(json.get("name"));
		centre.setShortName(json.get("shortName"));
		centre.setRemoved("true".equalsIgnoreCase(json.get("removed")));
		releaseCentreService.update(centre);
		return hypermediaGenerator.getEntityHypermedia(centre, request, RELEASE_CENTRE_LINKS);
	}

	@RequestMapping("/{releaseCentreBusinessKey}")
	@ResponseBody
	public Map getReleaseCentre(@PathVariable String releaseCentreBusinessKey, HttpServletRequest request) {
		String authenticatedId = SecurityHelper.getSubject();
		ReleaseCentre centre = releaseCentreService.find(releaseCentreBusinessKey, authenticatedId);
		return hypermediaGenerator.getEntityHypermedia(centre, request, RELEASE_CENTRE_LINKS);
	}

}
