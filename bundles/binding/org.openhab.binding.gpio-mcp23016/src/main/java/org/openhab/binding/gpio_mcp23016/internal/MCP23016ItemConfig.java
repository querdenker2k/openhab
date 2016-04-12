package org.openhab.binding.gpio_mcp23016.internal;

import org.openhab.core.items.Item;
import org.openhab.io.gpio_raspberry.item.GpioI2CItemConfig;

public class MCP23016ItemConfig extends GpioI2CItemConfig {
    private String id;
    private byte port;
    private char bank;
    private boolean in;
    private int pollInterval;

    public MCP23016ItemConfig() {
        super();
    }

    public MCP23016ItemConfig(Item item, String id, byte port, char bank, boolean in, int pollInterval) {
        super(item);
        this.id = id;
        this.port = port;
        this.bank = bank;
        this.in = in;
        this.pollInterval = pollInterval;
    }

    public byte getPort() {
        return port;
    }

    public void setPort(byte port) {
        this.port = port;
    }

    public char getBank() {
        return bank;
    }

    public void setBank(char bank) {
        this.bank = bank;
    }

    public boolean isIn() {
        return in;
    }

    public void setIn(boolean in) {
        this.in = in;
    }

    public int getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(int pollInterval) {
        this.pollInterval = pollInterval;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}
