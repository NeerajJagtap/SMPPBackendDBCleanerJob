package com.vuclip.smpp.db.clean.service;

import java.math.BigInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.SessionFactoryObserver;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.registry.internal.StandardServiceRegistryImpl;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

public class SMPPDBCleaner {

	private static final Logger smpplogger = LogManager.getLogger("smppDBCleanerLogger");

	private static final Logger smppCurrentBDStatus = LogManager.getLogger("smppDBCurrentStatus");

	public static void main(String args[]) {
		if (smpplogger.isDebugEnabled()) {
			smpplogger.debug("SMPPDBCleaner Started");
		}
		SessionFactory sessionFactory = null;
		Session session = null;
		Query query = null;
		String deleteRecsHQL = "Delete from smpp_data where talend_response = '200' and resp_status = '202' and modified_date <= (current_timestamp() - interval 2 hour)";
		String requestsInLast2HoursHQL = "Select count(*) from smpp_data where modified_date >= (current_timestamp() - interval 2 hour)";
		String failRequestsOverLast2hours = "Select count(*) from smpp_data where resp_status <> '202' and modified_date >= (current_timestamp() - interval 2 hour)";

		String responseOKRequestsInLast2HoursHQL = "Select count(*) from smpp_data where resp_status = '202' and modified_date >= (current_timestamp() - interval 2 hour)";
		String talendRespFailHQL = "Select count(*) from smpp_data where talend_response <> '200' and resp_status = '202' and modified_date >= (current_timestamp() - interval 2 hour)";
		try {
			sessionFactory = createSessionFactory();
			session = sessionFactory.getCurrentSession();
			session.beginTransaction();

			// Log Request made in last 2 hours
			query = session.createSQLQuery(requestsInLast2HoursHQL);
			BigInteger requestsMade = (BigInteger) query.uniqueResult();
			// Request failed by carrier in last 2 hours
			query = session.createSQLQuery(failRequestsOverLast2hours);
			BigInteger failRequestsToCarrier = (BigInteger) query.uniqueResult();
			// Request success by carrier in last 2 hours
			query = session.createSQLQuery(responseOKRequestsInLast2HoursHQL);
			BigInteger successReqs = (BigInteger) query.uniqueResult();
			// requests failed to talend in last 2 hours
			query = session.createSQLQuery(talendRespFailHQL);
			BigInteger talendNotReached = (BigInteger) query.uniqueResult();
			if (smppCurrentBDStatus.isInfoEnabled()) {
				smppCurrentBDStatus.info("==================== DB status in last 2 hours ==================");
				smppCurrentBDStatus.info("Requests Made : " + requestsMade);
				smppCurrentBDStatus.info("Failed from carrier : " + failRequestsToCarrier);
				smppCurrentBDStatus.info("Successful from Carrier : " + successReqs);
				smppCurrentBDStatus.info("Talend response not OK : " + talendNotReached);
				smppCurrentBDStatus.info("Success Requests : " + (requestsMade.longValue()
						- (failRequestsToCarrier.longValue() + talendNotReached.longValue())));
			}

			// Delete Recs before currentTime - 2hours
			query = session.createSQLQuery(deleteRecsHQL);
			int result = query.executeUpdate();
			session.getTransaction().commit();
			if (smpplogger.isDebugEnabled()) {
				smpplogger.debug("SMPPDBCleaner End with no of rows deleted : " + result);
			}
		} catch (Exception e) {
			String message = e.getMessage();
			if (smpplogger.isDebugEnabled()) {
				smpplogger.debug("SMPPDBCleaner End with Exception : " + message);
			}
			if (smppCurrentBDStatus.isInfoEnabled()) {
				smppCurrentBDStatus.info("Exception while reading " + message);
			}
		} finally {
			if (null != session && null != sessionFactory) {
				if (session.isOpen()) {
					session.close();
				}
				sessionFactory.close();
			}
		}
	}

	public static SessionFactory createSessionFactory() {
		Configuration configuration = new Configuration();
		configuration.configure("hibernate-config.xml");
		final ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
				.applySettings(configuration.getProperties()).build();
		// To close this running thread
		configuration.setSessionFactoryObserver(new SessionFactoryObserver() {

			private static final long serialVersionUID = 1L;

			public void sessionFactoryCreated(SessionFactory factory) {
				// do nothing
			}

			public void sessionFactoryClosed(SessionFactory factory) {
				((StandardServiceRegistryImpl) serviceRegistry).destroy();

			}
		});
		return configuration.buildSessionFactory(serviceRegistry);
	}
}
