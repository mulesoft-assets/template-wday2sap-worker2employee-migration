/**
 * Mule Anypoint Template
 *
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 */

package org.mule.templates.integration;


import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.tck.junit4.rule.DynamicPort;

import com.mulesoft.module.batch.BatchTestHelper;

/**
 * The objective of this class is to validate the correct behavior of the flows
 * for this Anypoint Template that make calls to external systems.
 */
public class BusinessLogicIT extends AbstractTemplateTestCase {

	private static final long TIMEOUT_SEC = 600;
	private static final long DELAY_MILLIS = 500;
	private static final String PATH_TO_TEST_PROPERTIES = "./src/test/resources/mule.test.properties";
	private static final String TEST_USER_NAME_PREFIX = "test";
	private static final Logger LOGGER = LogManager.getLogger(BusinessLogicIT.class);
	
	private static String WORKDAY_ID;
	private static String EMAIL_SUFFIX = "@template.com";
	
	private BatchTestHelper helper;
	private SubflowInterceptingChainLifecycleWrapper getSapEmployeeByEmailFlow;
	private SubflowInterceptingChainLifecycleWrapper getSapEmployeeByNameFlow;
	private SubflowInterceptingChainLifecycleWrapper deleteSapEmployeeFlow;
	private SubflowInterceptingChainLifecycleWrapper updateWorkersEmailFlow;
	private SubflowInterceptingChainLifecycleWrapper updateWorkersNameFlow;

	@Rule
	public DynamicPort port = new DynamicPort("http.port");

	/**
	 * Set up variables.
	 */
	@BeforeClass
	public static void beforeTestClass() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		System.setProperty("migration.startDate", sdf.format(new Date()));
	}

	/**
	 * Setting up before test.
	 * @throws Exception
	 */
	@Before
	public void setUp() throws Exception {
		final Properties props = new Properties();
		try {
			props.load(new FileInputStream(PATH_TO_TEST_PROPERTIES));
		} catch (Exception e) {
			LOGGER.error("Error occured while reading mule.test.properties", e);
		}
		
		WORKDAY_ID = props.getProperty("wday.testuser.id");
		helper = new BatchTestHelper(muleContext); 
		initializeSubFlows();
	}
	
	@AfterClass
	public static void afterTestClass() {		
		System.clearProperty("migration.startDate");
	}

	/**
	 * Initializes all the subflows
	 * @throws InitialisationException
	 */
	private void initializeSubFlows() throws InitialisationException {
		getSapEmployeeByEmailFlow = getSubFlow("getSAPEmployeeByEmail");
		getSapEmployeeByEmailFlow.initialise();
		
		getSapEmployeeByNameFlow = getSubFlow("getSAPEmployeeByName");
		getSapEmployeeByNameFlow.initialise();
		
		deleteSapEmployeeFlow = getSubFlow("terminateSAPEmployee");
		deleteSapEmployeeFlow.initialise();
		
		updateWorkersNameFlow = getSubFlow("updateWorkdayEmployeeName");
		updateWorkersNameFlow.initialise();
		
		updateWorkersEmailFlow = getSubFlow("updateWorkdayEmployeeEmail");
		updateWorkersEmailFlow.initialise();
	}


	/**
	 *  This tests worker creation - changing the e-mail can be thought of as
	 *  creating new user - we should see new SAP user created.
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testCreateFlow() throws Exception {
		// change worker's email in Workday
		Map<String, String> worker = generateWorkerEmailPayload();
		updateWorkersEmailFlow.process(getTestEvent(worker, MessageExchangePattern.REQUEST_RESPONSE));

		// migration happens here
		runMainFlow();

		// retrieve data from SAP
		MuleEvent response = getSapEmployeeByEmailFlow.process(getTestEvent(worker, MessageExchangePattern.REQUEST_RESPONSE));
		Map<String,String> sapEmployee = (Map<String,String>) response.getMessage().getPayload();
		LOGGER.info("sap employee after create: " + sapEmployee);
		
		Assert.assertNotNull("SAP Employee should have been synced", sapEmployee);
		Assert.assertNotNull("First name should be fetched", sapEmployee.get("FirstName"));
		Assert.assertNotNull("Last name should be fetched", sapEmployee.get("LastName"));

		// remove test data from SAP, moved here as @After would cause the
		// redundant and invalid remove call
		deleteSapEmployeeFlow.process(getTestEvent(sapEmployee));
	}

	/**
	 * This tests worker update - changing the name only - we should see the
	 * existing SAP user name updated.
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testUpdateFlow() throws Exception {
		// change worker's name in Workday
		Map<String,String> worker = generateWorkerNamePayload();
		updateWorkersNameFlow.process(getTestEvent(worker, MessageExchangePattern.REQUEST_RESPONSE));

		// migration happens here
		runMainFlow();

		// retrieve data from SAP
		MuleEvent response = getSapEmployeeByNameFlow.process(getTestEvent(worker,	MessageExchangePattern.REQUEST_RESPONSE));
		Map<String, String> sapEmployee = (Map<String, String>) response.getMessage().getPayload();
		LOGGER.info("sap employee: " + sapEmployee);
		
		Assert.assertNotNull("SAP Employee should have been synced", sapEmployee);
		Assert.assertEquals("First name should match", worker.get("FirstName"), sapEmployee.get("FirstName"));
		Assert.assertEquals("Last name should match", worker.get("LastName"), sapEmployee.get("LastName"));
	}

	/* HELPERS */

	/**
	 * Runs the migration flow
	 * @throws Exception
	 */
	private void runMainFlow() throws Exception {
		Thread.sleep(5000);
		runFlow("mainFlow");
		// Wait for the batch job executed by the flow to finish
		helper.awaitJobTermination(TIMEOUT_SEC * 1000, DELAY_MILLIS);
		helper.assertJobWasSuccessful();
	}
	
	/**
	 *  Helper method to generate employee object
	 * @return Map<String,String> with name and surname to be set.
	 */
	private Map<String,String> generateWorkerNamePayload() {
		Map<String,String> employee = new HashMap<>();
		employee.put("FirstName", TEST_USER_NAME_PREFIX);
		employee.put("LastName", TEST_USER_NAME_PREFIX + System.currentTimeMillis());
		employee.put("MiddleName", "");
		employee.put("Id", WORKDAY_ID);
		return employee;
	}

	/**
	 *  Helper method to generate Map with unique e-mail
	 * @return HashMap<String,String> containing the Workday ID and unique E-mail of test worker.
	 */
	private Map<String, String> generateWorkerEmailPayload() {
		Map<String, String> employee = new HashMap<String, String>();
		employee.put("Email", System.currentTimeMillis() + EMAIL_SUFFIX);
		employee.put("Id", WORKDAY_ID);
		LOGGER.debug("E-mail: " + employee.get("Email")); 
		return employee;
	}

}