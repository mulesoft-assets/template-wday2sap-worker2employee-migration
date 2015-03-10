/**
 * Mule Anypoint Template
 *
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 */

package org.mule.templates;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import com.workday.hr.EffectiveAndUpdatedDateTimeDataType;
import com.workday.hr.GetWorkersRequestType;
import com.workday.hr.TransactionLogCriteriaType;
import com.workday.hr.WorkerRequestCriteriaType;
import com.workday.hr.WorkerResponseGroupType;

public class WorkersRequest {

	public static GetWorkersRequestType create(String startDate)
			throws ParseException, DatatypeConfigurationException {

		GetWorkersRequestType workersCriteria = new GetWorkersRequestType();

		// time interval to consider when building response
		EffectiveAndUpdatedDateTimeDataType dateRangeData = new EffectiveAndUpdatedDateTimeDataType();
		// from
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
		dateRangeData.setUpdatedFrom(xmlDate(sdf.parse(startDate)));
		// to
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.SECOND, -6);
		dateRangeData.setUpdatedThrough(xmlDate(cal.getTime()));

		// transaction log criteria - what time range to consider
		List<TransactionLogCriteriaType> transactionLogCriteriaData = new ArrayList<TransactionLogCriteriaType>();
		TransactionLogCriteriaType log = new TransactionLogCriteriaType();
		log.setTransactionDateRangeData(dateRangeData);
		transactionLogCriteriaData.add(log);

		// request criteria
		WorkerRequestCriteriaType crit = new WorkerRequestCriteriaType();
		crit.setTransactionLogCriteriaData(transactionLogCriteriaData);
		// exclude inactive workers only (return employee and contingent workers)
		crit.setExcludeInactiveWorkers(true);
		workersCriteria.setRequestCriteria(crit);

		// type of data to respond with
		WorkerResponseGroupType resGroup = new WorkerResponseGroupType();
		resGroup.setIncludePersonalInformation(true);

		workersCriteria.setResponseGroup(resGroup);
		return workersCriteria;	
}

	private static XMLGregorianCalendar xmlDate(Date date)
			throws DatatypeConfigurationException {
		GregorianCalendar gregorianCalendar = (GregorianCalendar) GregorianCalendar.getInstance();
		gregorianCalendar.setTime(date);
		return DatatypeFactory.newInstance().newXMLGregorianCalendar(gregorianCalendar);
	}

}
