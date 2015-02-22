package org.openhab.io.gpio_raspberry.device;

import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.io.gpio_raspberry.item.GpioItemConfig;

public abstract class Device<DC extends DeviceConfig, IC extends GpioItemConfig> {
	protected DC config;
	
	public Device() {
		super();
	}
	
	public Device(DC config) {
		super();
		this.config = config;
	}

	public abstract State communicate(Command command, IC itemConfig, State state);

	public DC getConfig() {
		return config;
	}

	public void setConfig(DC config) {
		this.config = config;
	}
	
	
}
