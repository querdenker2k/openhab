package org.openhab.binding.gpio_mcp23016.internal;

import org.openhab.io.gpio_raspberry.device.I2CConfig;

public class MCP23016Config extends I2CConfig {

	public MCP23016Config(String id) {
		super(id);
	}

	public MCP23016Config(String id, byte address) {
		super(id, address);
	}
	
}
