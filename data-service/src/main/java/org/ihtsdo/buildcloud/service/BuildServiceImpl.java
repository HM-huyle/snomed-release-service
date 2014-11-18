package org.ihtsdo.buildcloud.service;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.hibernate.Hibernate;
import org.ihtsdo.buildcloud.dao.BuildDAO;
import org.ihtsdo.buildcloud.dao.ProductDAO;
import org.ihtsdo.buildcloud.entity.Build;
import org.ihtsdo.buildcloud.entity.Product;
import org.ihtsdo.buildcloud.entity.User;
import org.ihtsdo.buildcloud.entity.helper.EntityHelper;
import org.ihtsdo.buildcloud.service.exception.BadRequestException;
import org.ihtsdo.buildcloud.service.exception.BusinessServiceException;
import org.ihtsdo.buildcloud.service.exception.EntityAlreadyExistsException;
import org.ihtsdo.buildcloud.service.exception.ResourceNotFoundException;
import org.ihtsdo.buildcloud.service.helper.CompositeKeyHelper;
import org.ihtsdo.buildcloud.service.helper.FilterOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.util.*;

@Service
@Transactional
public class BuildServiceImpl extends EntityServiceImpl<Build> implements BuildService {

	private static final Logger LOGGER = LoggerFactory.getLogger(BuildServiceImpl.class);

	@Autowired
	private BuildDAO buildDAO;

	@Autowired
	private ProductDAO productDAO;

	@Autowired
	public BuildServiceImpl(BuildDAO dao) {
		super(dao);
	}

	@Override
	public List<Build> findAll(Set<FilterOption> filterOptions, User authenticatedUser) {
		return buildDAO.findAll(filterOptions, authenticatedUser);
	}

	@Override
	public Build find(String buildCompositeKey, User authenticatedUser) throws ResourceNotFoundException {
		Long id = CompositeKeyHelper.getId(buildCompositeKey);
		if (id == null) {
			throw new ResourceNotFoundException("Unable to find build: " + buildCompositeKey);
		}
		return buildDAO.find(id, authenticatedUser);
	}

	@Override
	public Build find(String releaseCenterBusinessKey,
			String extensionBusinessKey, String productBusinessKey, String buildBusinessKey,
			User authenticatedUser) throws ResourceNotFoundException {
		Build build = buildDAO.find(releaseCenterBusinessKey, extensionBusinessKey, productBusinessKey, buildBusinessKey, authenticatedUser);
		if (build == null) {
			throw new ResourceNotFoundException("Unable to find build: " + buildBusinessKey);
		}
		return build;
	}

	@Override
	public List<Build> findForExtension(String releaseCenterBusinessKey, String extensionBusinessKey, Set<FilterOption> filterOptions, User authenticatedUser) {
		List<Build> builds = buildDAO.findAll(releaseCenterBusinessKey, extensionBusinessKey, filterOptions, authenticatedUser);
		Hibernate.initialize(builds);
		return builds;
	}

	@Override
	public List<Build> findForProduct(String releaseCenterBusinessKey, String extensionBusinessKey, String productBusinessKey, User authenticatedUser) throws ResourceNotFoundException {
		Product product = productDAO.find(releaseCenterBusinessKey, extensionBusinessKey, productBusinessKey, authenticatedUser);

		if (product == null) {
			String item = CompositeKeyHelper.getPath(releaseCenterBusinessKey, extensionBusinessKey, productBusinessKey);
			throw new ResourceNotFoundException("Unable to find product: " + item);
		}
		List<Build> builds = product.getBuilds();
		Hibernate.initialize(builds);
		return builds;
	}

	@Override
	public Build create(String releaseCenterBusinessKey, String extensionBusinessKey, String productBusinessKey, String name, User authenticatedUser) throws BusinessServiceException {
	    LOGGER.info("create build, releaseCenterBusinessKey: {}, extensionBusinessKey: {}", releaseCenterBusinessKey, extensionBusinessKey);
	    Product product = productDAO.find(releaseCenterBusinessKey, extensionBusinessKey, productBusinessKey, authenticatedUser);

		if (product == null) {
			String item = CompositeKeyHelper.getPath(releaseCenterBusinessKey, extensionBusinessKey, productBusinessKey);
			throw new ResourceNotFoundException("Unable to find product: " + item);
		}

		//Check that we don't already have one of these
		String buildBusinessKey = EntityHelper.formatAsBusinessKey(name);
		Build existingBuild = buildDAO.find(releaseCenterBusinessKey, extensionBusinessKey, productBusinessKey, buildBusinessKey, authenticatedUser);
		if (existingBuild != null) {
			throw new EntityAlreadyExistsException(name + " already exists.");
		}


		Build build = new Build(name);
		product.addBuild(build);
		buildDAO.save(build);
		return build;
	}

	@Override
	public Build update(String buildCompositeKey, Map<String, String> newPropertyValues, User authenticatedUser) throws BusinessServiceException {
		LOGGER.info("update build, newPropertyValues: {}", newPropertyValues);
		Build build = find(buildCompositeKey, authenticatedUser);

		if (build == null) {
			throw new ResourceNotFoundException("Unable to find build: " + buildCompositeKey);
		}
		if (newPropertyValues.containsKey(EFFECTIVE_TIME)) {
			try {
				Date date = DateFormatUtils.ISO_DATE_FORMAT.parse(newPropertyValues.get(EFFECTIVE_TIME));
				build.setEffectiveTime(date);
			} catch (ParseException e) {
				throw new BadRequestException("Invalid " + EFFECTIVE_TIME + " format. Expecting format " + DateFormatUtils.ISO_DATE_FORMAT.getPattern() + ".", e);
			}
		}
		buildDAO.update(build);
		return build;
	}

}
