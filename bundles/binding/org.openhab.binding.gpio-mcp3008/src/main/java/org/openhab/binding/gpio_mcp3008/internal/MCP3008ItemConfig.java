package org.openhab.binding.gpio_mcp3008.internal;

import org.openhab.core.items.Item;
import org.openhab.io.gpio_raspberry.item.GpioSPIItemConfig;

public class MCP3008ItemConfig extends GpioSPIItemConfig {
	private double factor;
	private double offset;
	private long meterings;
	private byte port;
	private int refresh;

	public MCP3008ItemConfig(Item item, byte port, double factor, double offset, long meterings, int refresh) {
		super(item);
		this.factor = factor;
		this.offset = offset;
		this.meterings = meterings;
		this.port = port;
		this.refresh = refresh;
	}

	public double getFactor() {
		return factor;
	}

	public void setFactor(double factor) {
		this.factor = factor;
	}

	public double getOffset() {
		return offset;
	}

	public void setOffset(double offset) {
		this.offset = offset;
	}

	public long getMeterings() {
		return meterings;
	}

	public void setMeterings(long meterings) {
		this.meterings = meterings;
	}

	public byte getPort() {
		return port;
	}

	public void setPort(byte port) {
		this.port = port;
	}

	public int getRefresh() {
		return refresh;
	}

	public void setRefresh(int refresh) {
		this.refresh = refresh;
	}
	
	
}