package org.openhab.binding.gpio_mcp23017.internal;

import org.openhab.core.items.Item;
import org.openhab.io.gpio_raspberry.item.GpioI2CItemConfig;

public class MCP23017ItemConfig extends GpioI2CItemConfig {
    private String id;
    private byte port;
    private char bank;
    private boolean in;
    private boolean activeLow;

    public MCP23017ItemConfig() {
        super();
    }

    public MCP23017ItemConfig(Item item, String id, byte port, char bank, boolean in, boolean activeLow) {
        super(item);
        this.id = id;
        this.port = port;
        this.bank = bank;
        this.in = in;
        this.activeLow = activeLow;
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isActiveLow() {
        return activeLow;
    }

    public void setActiveLow(boolean activeLow) {
        this.activeLow = activeLow;
    }

}
