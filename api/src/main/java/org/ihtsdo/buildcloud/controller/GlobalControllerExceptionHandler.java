package org.ihtsdo.buildcloud.controller;

import org.ihtsdo.buildcloud.service.exception.BadConfigurationException;
import org.ihtsdo.buildcloud.service.exception.BadRequestException;
import org.ihtsdo.buildcloud.service.exception.EntityAlreadyExistsException;
import org.ihtsdo.buildcloud.service.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;

import java.util.HashMap;

@ControllerAdvice
public class GlobalControllerExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalControllerExceptionHandler.class);

	@ExceptionHandler(BadRequestException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ResponseBody
	public HashMap<String, String> handleBadRequestError(Exception exception, HttpServletRequest request) {
		logError(request, exception);
		return getErrorMap(exception);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	@ResponseBody
	public HashMap<String, String> handleResourceNotFoundError(Exception exception, HttpServletRequest request) {
		logError(request, exception);
		return getErrorMap(exception);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ResponseBody
	public HashMap<String, String> handleMissingServletRequestParameterException(Exception exception, HttpServletRequest request) {
		logError(request, exception);
		return getErrorMap(exception);
	}

	@ExceptionHandler(BadConfigurationException.class)
	@ResponseStatus(HttpStatus.PRECONDITION_FAILED)
	@ResponseBody
	public HashMap<String, String> handleBadConfigurationException(Exception exception, HttpServletRequest request) {
		logError(request, exception);
		return getErrorMap(exception);
	}

	@ExceptionHandler(EntityAlreadyExistsException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	@ResponseBody
	public HashMap<String, String> handleEntityAlreadyExistsException(Exception exception, HttpServletRequest request) {
		logError(request, exception);
		return getErrorMap(exception);
	}

	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ResponseBody
	public HashMap<String, String> handleError(Exception exception, HttpServletRequest request) {
		logError(request, exception);
		return getErrorMap(exception);
	}

	private HashMap<String, String> getErrorMap(Exception exception) {
		HashMap<String, String> errorObject = new HashMap<>();
		errorObject.put(ControllerConstants.MESSAGE, exception.getLocalizedMessage());
		return errorObject;
	}

	private void logError(HttpServletRequest request, Exception exception) {
		LOGGER.error("Request '{}' raised:", request.getRequestURL(), exception);
	}

}