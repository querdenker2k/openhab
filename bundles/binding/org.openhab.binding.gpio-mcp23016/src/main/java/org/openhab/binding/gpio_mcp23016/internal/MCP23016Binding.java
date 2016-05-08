/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.gpio_mcp23016.internal;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.openhab.binding.gpio_mcp23016.MCP23016BindingProvider;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.io.gpio_raspberry.GpioException;
import org.openhab.io.gpio_raspberry.GpioLoader;
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
public class MCP23016Binding extends AbstractActiveBinding<MCP23016BindingProvider>implements ManagedService {

    private static final Logger logger = LoggerFactory.getLogger(MCP23016Binding.class);

    private static final String PROP_ADDRESS = "address";
    private static final String PROP_POLL_INTERVAL = "pollInterval";

    private long minimumRefresh = 5000;

    private GpioLoader gpioLoader;
    private ItemRegistry itemRegistry;

    private Map<String, MCP23016Device> deviceMap = new HashMap<>();

    public MCP23016Binding() {
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

    @Override
    public void activate() {
        super.activate();
    }

    @Override
    public void deactivate() {
        for (MCP23016Device device : this.deviceMap.values()) {
            device.stopPolling();
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

        for (MCP23016BindingProvider provider : providers) {

            MCP23016ItemConfig itemConfig = provider.getItemConfig(itemName);
            Item item = null;
            try {
                item = this.itemRegistry.getItem(itemName);
            } catch (ItemNotFoundException e) {
                logger.error("cannot find item: " + itemName);
                return;
            }
            try {
                State state = deviceMap.get(itemConfig.getId()).communicate(command, itemConfig, item.getState());
                if (state == null) {
                    logger.debug("no state returned, do not publish");
                    continue;
                }
                super.eventPublisher.postUpdate(itemName, state);
            } catch (Exception e) {
                logger.error("error communicating with device: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        if (properties != null) {
            logger.info("loading configuration");
            Enumeration<String> keys = properties.keys();
            Map<String, MCP23016Config> configMap = new HashMap<>();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                if (!key.equals("service.pid")) {
                    logger.trace("reading config entry: {}", key);
                    String[] split = key.split("\\.");
                    String id = split[0];
                    if (!configMap.containsKey(id)) {
                        configMap.put(id, new MCP23016Config(id));
                    }

                    if (split[1].equals(PROP_ADDRESS)) {
                        byte address = Byte.parseByte((String) properties.get(key), 16);
                        configMap.get(id).setAddress(address);
                    } else if (split[1].equals(PROP_POLL_INTERVAL)) {
                        int pollInterval = Integer.parseInt(properties.get(key) + "");
                        configMap.get(id).setPollInterval(pollInterval);
                    }
                }
            }

            for (String id : configMap.keySet()) {
                MCP23016Config mcp23016Config = configMap.get(id);
                logger.debug("******************");
                logger.debug("id: " + mcp23016Config.getId());
                logger.debug("address: " + String.format("%02x", mcp23016Config.getAddress()));
                logger.debug("pollInterval: " + mcp23016Config.getPollInterval());
                logger.debug("******************");

                try {
                    this.deviceMap.put(id,
                            (MCP23016Device) this.gpioLoader.createI2CDevice(mcp23016Config, MCP23016Device.class));
                } catch (GpioException e) {
                    logger.error(e.getMessage());
                }

            }

            setProperlyConfigured(true);
        }
    }

    @Override
    protected void execute() {
        for (MCP23016BindingProvider provider : providers) {
            for (String itemName : provider.getItemNames()) {
                MCP23016ItemConfig itemConfig = provider.getItemConfig(itemName);
                if (itemConfig.isIn()) {
                    this.deviceMap.get(itemConfig.getId()).startPolling(itemConfig, eventPublisher);
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
        return "GPIO-mcp23016 Service";
    }
}
