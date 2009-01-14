package com.rabbitmq.client.osgi.connection;

import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConnectionParameters;
import com.rabbitmq.client.osgi.common.CMUtils;
import com.rabbitmq.client.osgi.common.Pair;
import com.rabbitmq.client.osgi.common.ServiceProperties;

public class ConnectionServiceFactory implements ManagedServiceFactory {
	
	private static final String PROP_NAME = "name";
	private static final String PROP_HOST = "host";
	private static final String PROP_PORT = "port";
	private static final String PROP_USERNAME = "username";
	private static final String PROP_PASSWORD = "password";
	private static final String PROP_VIRTUAL_HOST = "vhost";
	private static final String PROP_REQ_HEARTBEAT = "heartbeat";
	
	private final Map<String, Pair<Connection, ServiceRegistration>> map = new HashMap<String, Pair<Connection, ServiceRegistration>>();
	private final BundleContext context;
	
	public ConnectionServiceFactory(BundleContext context) {
		this.context = context;
	}

	public void deleted(String pid) {
		Pair<Connection,ServiceRegistration> pair = null;
		synchronized (map) {
			pair = map.remove(pid);
		}
		if(pair != null) {
			pair.getSnd().unregister();
			try {
				pair.getFst().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public String getName() {
		return "Channel Service Factory";
	}

	public void updated(String pid, @SuppressWarnings("unchecked") Dictionary props)
			throws ConfigurationException {
		// Process Properties
		String host = CMUtils.getMandatoryString(PROP_HOST, props);
		Integer portObj = CMUtils.getInteger(PROP_PORT, props);
	
		ConnectionParameters params = new ConnectionParameters();
		params.setUsername(CMUtils.getMandatoryString(PROP_USERNAME, props));
		params.setPassword(CMUtils.getMandatoryString(PROP_PASSWORD, props));
		params.setVirtualHost(CMUtils.getMandatoryString(PROP_VIRTUAL_HOST, props));
		
		Integer reqHeartbeat = CMUtils.getInteger(PROP_REQ_HEARTBEAT, props);
		if(reqHeartbeat != null) {
			params.setRequestedHeartbeat(reqHeartbeat.intValue());
		}
		
		// If name not specified, set to "username@host:port".
		String name = CMUtils.getString(PROP_NAME, props);
		if(name == null) {
			StringBuilder buf = new StringBuilder();
			buf.append(params.getUserName()).append(params.getUserName()).append('@').append(host);
			if(portObj != null) {
				buf.append(':').append(portObj.intValue());
			}
			name = buf.toString();
		}
		
		// Create the new connection & service
		Pair<Connection, ServiceRegistration> connPair = null;
		try {
			ConnectionFactory connFactory = new ConnectionFactory(params);
			Connection conn = connFactory.newConnection(host, portObj == null ? -1 : portObj.intValue());
			
			Properties svcProps = new Properties();
			svcProps.put(ServiceProperties.CONNECTION_NAME, name);
			svcProps.put(ServiceProperties.CONNECTION_HOST, host);
			ServiceRegistration reg = context.registerService(Connection.class.getName(), conn, svcProps);
			
			connPair = new Pair<Connection, ServiceRegistration>(conn, reg);
		} catch (IOException e) {
			throw new ConfigurationException(null, "Error connecting to broker", e);
		}
		
		// Replace in the map
		Pair<Connection, ServiceRegistration> old = null;
		synchronized (map) {
			old = map.put(pid, connPair);
		}
		
		// Unregister and destroy the old connection with this PID (if any)
		if(old != null) {
			old.getSnd().unregister();
			try {
				old.getFst().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	

}


