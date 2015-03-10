/**
 * Mule Anypoint Template
 *
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 */

package org.mule.templates.integration;

import static org.junit.Assert.assertNotNull;

import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.templates.utils.Employee;
import org.mule.transport.NullPayload;

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
	private BatchTestHelper helper;
	private static String WORKDAY_ID;
	private static String EMAIL_SUFFIX = "@template.com";


	/**
	 * Set up variables.
	 */
	@BeforeClass
	public static void beforeTestClass() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
		Calendar cal = Calendar.getInstance();
		System.setProperty("migration.startDate", "\"" + sdf.format(cal.getTime()) + "\"");
		System.setProperty("mule.test.timeoutSecs", "600");
		
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
			logger.error("Error occured while reading mule.test.properties", e);
		}
		WORKDAY_ID = props.getProperty("wday.testuser.id");
	}
	

	/**
	 * This tests both Worker update and creation (e-mail change).
	 * Worker update case means that we change his name. SAP Employee name should be updated.
	 * Worker creation means that we change its e-mail. New SAP Employee should be created.
	 * @throws Exception
	 */
	@Test
	public void testUpdateAndCreateCase() throws Exception {
		helper = new BatchTestHelper(muleContext); 
		testUpdateFlow();

		helper = new BatchTestHelper(muleContext); 
		testCreateFlow();
	}
	

	/**
	 *  This tests worker creation - changing the e-mail can be thought of as
	 *  creating new user - we should see new SAP user created.
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public void testCreateFlow() throws Exception {
		Map<String, String> workerEmailMap = generateWorkerEmailPayload();
		updateTestWorkersEmail(workerEmailMap);

		// migration happens here
		runMainFlow();

		// prepare the verification flow
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("getSAPEmployeeByEmail");
		flow.initialise();

		// run it
		MuleEvent response = flow.process(getTestEvent(workerEmailMap.get("Email"),	MessageExchangePattern.REQUEST_RESPONSE));

		// response and assertion - SAP employee should be inserted - we should see the employee in the response
		Map<String,String> sapEmployee;
		if(response.getMessage().getPayload() instanceof NullPayload) {
			sapEmployee = null;
		}
		else {
			sapEmployee = (Map<String, String>) response.getMessage().getPayload();
		}
		logger.info("sap employee after create: " + sapEmployee);
		assertNotNull("SAP Employee should have been synced", sapEmployee);

		// remove test data from SAP, moved here as @After would cause the
		// redundant and invalid remove call
		deleteTestEmployeeFromSAP(sapEmployee.get("id"));

	}

	// 
	/**
	 * This tests worker update - changing the name only - we should see the
	 * existing SAP user name updated.
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public void testUpdateFlow() throws Exception {
		Employee worker = generateWorkerNamePayload();
		updateTestWorkersName(worker);

		// migration happens here
		runMainFlow();

		// prepare the verification flow
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("getSAPEmployeeByName");
		flow.initialise();
		Map<String, String> mapWithName = new HashMap<String, String>();
		mapWithName.put("firstName", worker.getGivenName());
		mapWithName.put("lastName", worker.getFamilyName());

		// run it
		MuleEvent response = flow.process(getTestEvent(mapWithName,	MessageExchangePattern.REQUEST_RESPONSE));

		// response and assertion
		Map<String, String> sapEmployee;
		if (response.getMessage().getPayload() instanceof NullPayload) {
			sapEmployee = null;
		} else {
			sapEmployee = (Map<String, String>) response.getMessage().getPayload();
		}
		logger.info("sap employee: " + sapEmployee);
		assertNotNull("SAP Employee should have been synced", sapEmployee);
	}

	/* SUBFLOWS */
	
	/**
	 * Trigger change on test worker's name and surname.
	 * @param userPayload Employee object defining name and surname
	 * @throws MuleException
	 */
	private void updateTestWorkersName(Employee userPayload)
			throws MuleException {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("updateWorkdayEmployeeName");
		flow.initialise();
		logger.info("updating a workday employee name...");
		try {
			flow.process(getTestEvent(userPayload,
					MessageExchangePattern.REQUEST_RESPONSE));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Trigger change on test worker's E-mail - we won't create a new one. 
	 * This simulates creation of worker.
	 * @param userPayload Map containing test user's E-mail and Workday ID.
	 * @throws MuleException
	 */
	private void updateTestWorkersEmail(Map<String, String> userPayload)
			throws MuleException {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("updateWorkdayEmployeeEmail");
		flow.initialise();
		logger.info("updating a workday employee e-mail...");
		try {
			flow.process(getTestEvent(userPayload, MessageExchangePattern.REQUEST_RESPONSE));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Runs the migration flow
	 * @throws Exception
	 */
	private void runMainFlow() throws InterruptedException, Exception,
			InitialisationException, MuleException {
		Thread.sleep(10000);
		runFlow("mainFlow");
		// Wait for the batch job executed by the flow to finish
		helper.awaitJobTermination(TIMEOUT_SEC * 1000, DELAY_MILLIS);
		helper.assertJobWasSuccessful();
	}

	/**
	 * Cleanup from SAP - terminate the Employee after the tests are done.
	 * @param id Workday ID of test user
	 * @throws Exception
	 */
	private void deleteTestEmployeeFromSAP(String id) throws MuleException,
			Exception {
		logger.info("deleting test employee from SAP: " + id);
		SubflowInterceptingChainLifecycleWrapper flowDelete = getSubFlow("terminateSAPEmployee");
		flowDelete.initialise();
		Map<String, String> map = new HashMap<String, String>();
		map.put("id", id);
		flowDelete.process(getTestEvent(map));
	}

	/* HELPERS */
	
	/**
	 *  Helper method to generate employee object
	 * @return Employee object with name and surname to be set.
	 */
	private Employee generateWorkerNamePayload() {
		Employee user = new Employee(TEST_USER_NAME_PREFIX,
				TEST_USER_NAME_PREFIX + System.currentTimeMillis(), "",	WORKDAY_ID);
		return user;
	}

	/**
	 *  Helper method to generate Map with unique e-mail
	 * @return HashMap<String,String> containing the Workday ID and unique E-mail of test worker.
	 */
	private Map<String, String> generateWorkerEmailPayload() {
		Map<String, String> emailUser = new HashMap<String, String>();
		emailUser.put("Email", System.currentTimeMillis() + EMAIL_SUFFIX);
		emailUser.put("Id", WORKDAY_ID);
		logger.debug("E-mail: " + emailUser.get("Email"));
		return emailUser;
	}

}