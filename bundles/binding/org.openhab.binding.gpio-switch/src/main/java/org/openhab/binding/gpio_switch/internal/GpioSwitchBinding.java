/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.gpio_switch.internal;

import java.util.Dictionary;

import org.openhab.binding.gpio_switch.GpioSwitchBindingProvider;

import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.io.gpio_raspberry.GpioException;
import org.openhab.io.gpio_raspberry.GpioLoader;
import org.openhab.io.gpio_raspberry.device.IOConfig;
import org.openhab.io.gpio_raspberry.device.IODevice;
import org.openhab.io.gpio_raspberry.item.GpioIOItemConfig;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
	

/**
 * Implement this class if you are going create an actively polling service
 * like querying a Website/Device.
 * 
 * @author Robert Delbrück
 * @since 1.6.0
 */
public class GpioSwitchBinding extends AbstractActiveBinding<GpioSwitchBindingProvider> implements ManagedService {

	private static final Logger logger = 
		LoggerFactory.getLogger(GpioSwitchBinding.class);
	
	private long minimumRefresh = 10000;
	
	private GpioLoader gpioLoader;
	private ItemRegistry itemRegistry;
	
	private IODevice device;

	
	public GpioSwitchBinding() {
	}
	
	public void setGpioLoader(GpioLoader gpioLoader) {
		this.gpioLoader = gpioLoader;
	}
	
	public void unsetGpioLoader(GpioLoader gpioLoader) {
		this.gpioLoader = null;
	}
	
	public void setItemRegistry(ItemRegistry itemRegistry) {
		this.itemRegistry = itemRegistry;
	}
	
	public void unsetItemRegistry(ItemRegistry itemRegistry) {
		this.itemRegistry = null;
	}
	
	public void activate() {
		super.activate();
	}
	
	public void deactivate() {
		if (this.device != null) {
			this.device.stopPolling();
		}
	}

	
	/**
	 * @{inheritDoc}
	 */
	@Override
	protected void internalReceiveCommand(String itemName, Command command) {
		// the code being executed when a command was sent on the openHAB
		// event bus goes here. This method is only called if one of the 
		// BindingProviders provide a binding for the given 'itemName'.
		logger.debug("internalReceiveCommand() is called!");
		
		for (GpioSwitchBindingProvider provider : providers) {
			
			GpioIOItemConfig itemConfig = provider.getItemConfig(itemName);
			Item item = null;
			try {
				item = this.itemRegistry.getItem(itemName);
			} catch (ItemNotFoundException e) {
				logger.error("cannot find item: " + itemName);
				return;
			}
			State state = device.communicate(command, itemConfig, item.getState());
			if (state == null) {
				logger.debug("no state returned, do not publish");
				continue;
			}
			
			super.eventPublisher.postUpdate(itemName, state);
		}
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	protected void internalReceiveUpdate(String itemName, State newState) {
		// the code being executed when a state was sent on the openHAB
		// event bus goes here. This method is only called if one of the 
		// BindingProviders provide a binding for the given 'itemName'.
		logger.debug("internalReceiveCommand() is called!");
	}


	@Override
	public void updated(Dictionary<String, ?> properties)
			throws ConfigurationException {
		IOConfig config = new IOConfig(this.getName());
		
		try {
			this.device = (IODevice) this.gpioLoader.createIODevice(config, IODevice.class);
		} catch (GpioException e) {
			logger.error(e.getMessage());
		}
		
		setProperlyConfigured(true);
	}


	@Override
	protected void execute() {
		if (providers.size() == 0) {
			logger.debug("no providers are set");
		}
		for (GpioSwitchBindingProvider provider : providers) {
			for (String itemName : provider.getItemNames()) {
				GpioIOItemConfig itemConfig = provider.getItemConfig(itemName);
				if (itemConfig.isIn()) {
					this.device.startPolling(itemConfig, eventPublisher);
				}
			}
		}
	}


	@Override
	protected long getRefreshInterval() {
		return minimumRefresh;
	}


	@Override
	protected String getName() {
		return "GPIO Switch";
	}
}