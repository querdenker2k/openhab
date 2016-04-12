package org.openhab.binding.gpio_mcp23016.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.openhab.core.events.EventPublisher;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.ContactItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.io.gpio_raspberry.device.I2CDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCP23016Device extends I2CDevice<MCP23016Config, MCP23016ItemConfig> {
    private static final Logger LOG = LoggerFactory.getLogger(MCP23016Device.class);

    private ExecutorService executors = Executors.newCachedThreadPool();
    private Map<Byte, Runnable> pollingMap = new HashMap<Byte, Runnable>();
    private List<String> directionSetList = new ArrayList<String>();

    public MCP23016Device(MCP23016Config config) {
        super(config);
    }

    public void startPolling(final MCP23016ItemConfig itemConfig, final EventPublisher eventPublisher) {
        if (pollingMap.containsKey(itemConfig.getPort())) {
            // it's already polling
            return;
        }

        final byte portMask = (byte) (0x01 << itemConfig.getPort());
        this.setDirection(portMask, itemConfig.getPort(), itemConfig.getBank(), true);

        PollingThread run = new PollingThread(portMask, itemConfig.getBank(), eventPublisher,
                itemConfig.getItem().getName(), itemConfig.getItem().getClass(), itemConfig.getPollInterval());
        this.pollingMap.put(itemConfig.getPort(), run);
        this.executors.execute(run);
    }

    public void stopPolling() {
        this.pollingMap.clear();
        this.executors.shutdownNow();
    }

    @Override
    public State communicate(Command command, final MCP23016ItemConfig itemConfig, State state) {
        if (command instanceof OnOffType) {
            OnOffType onOffType = (OnOffType) command;
            switch (onOffType) {
                case ON:
                    change(this.getConfig().getAddress(), itemConfig.getPort(), itemConfig.getBank(), true);
                    break;
                case OFF:
                    change(this.getConfig().getAddress(), itemConfig.getPort(), itemConfig.getBank(), false);
                    break;
                default:
                    throw new IllegalStateException("not implemented state: " + onOffType);
            }
            return null;
        } else if (command instanceof OpenClosedType) {
            OpenClosedType openClosedType = (OpenClosedType) command;
            switch (openClosedType) {
                case OPEN:
                    change(this.getConfig().getAddress(), itemConfig.getPort(), itemConfig.getBank(), true);
                    break;
                case CLOSED:
                    change(this.getConfig().getAddress(), itemConfig.getPort(), itemConfig.getBank(), false);
                    break;
                default:
                    throw new IllegalStateException("not implemented state: " + openClosedType);
            }
            return null;
        } else {
            throw new IllegalStateException("not implemented command-type: " + command);
        }
    }

    private void change(byte address, byte port, char bank, boolean on) {

        byte portAddress = (byte) (0x01 << port);
        byte registerSwitch = getRegisterSwitch(bank);
        byte registerDir = getRegisterDir(bank);

        LOG.debug("switch address={}, port={}, registerDir={}, registerPort={}",
                new Object[] { Integer.toHexString(address), Integer.toHexString(portAddress),
                        Integer.toHexString(registerDir), Integer.toHexString(registerSwitch) });

        this.setDirection(portAddress, port, bank, false);

        super.open("/dev/i2c-1");
        try {
            byte newValue;
            int oldValue = super.read(registerSwitch);
            LOG.debug("reading port register: " + String.format("%02x", oldValue));
            if (on) {
                newValue = (byte) (oldValue | portAddress);
            } else {
                newValue = (byte) (oldValue & ~portAddress);
            }
            LOG.debug("setting port register: " + String.format("%02x", newValue));
            boolean res = super.write(registerSwitch, newValue);
            if (!res) {
                throw new IllegalStateException("cannot read value on port, error='" + res + "'");
            }
        } finally {
            super.close();
        }
    }

    private static byte getRegisterSwitch(char bank) {
        if (bank == 'a') {
            return 0x00;
        } else if (bank == 'b') {
            return 0x01;
        } else {
            throw new IllegalStateException("unknown bank: " + bank);
        }
    }

    private static byte getRegisterDir(char bank) {
        if (bank == 'a') {
            return 0x06;
        } else if (bank == 'b') {
            return 0x07;
        } else {
            throw new IllegalStateException("unknown bank: " + bank);
        }
    }

    private void setDirection(byte portMask, byte port, char bank, boolean in) {
        if (directionSetList.contains(bank + "" + port)) {
            return;
        }
        byte registerDir = getRegisterDir(bank);

        LOG.debug("set port '{}' as {} (mask={})", port, in == true ? "input" : "output",
                Integer.toHexString(portMask));

        super.open("/dev/i2c-1");
        try {
            int oldValue = super.read(registerDir);
            LOG.trace("old mask: " + String.format("%02x", oldValue));
            byte newValue;
            // 1 is input
            if (in) {
                newValue = (byte) ((oldValue & 0xFF) | portMask);
            } else {
                newValue = (byte) ((oldValue & 0xFF) & ~portMask);
            }
            LOG.trace("new mask: " + String.format("%02x", newValue));
            if (oldValue == newValue) {
                LOG.debug("setting not necessary");
                return;
            }
            boolean res = super.write(registerDir, newValue);
            if (!res) {
                throw new IllegalStateException("cannot set port as input, error='" + res + "'");
            }
            directionSetList.add(bank + "" + port);
        } finally {
            super.close();
        }
    }

    private static void updateItem(EventPublisher eventPublisher, String itemName, boolean on,
            Class<? extends Item> itemType) {
        if (itemType.equals(SwitchItem.class)) {
            if (on) {
                eventPublisher.postUpdate(itemName, OnOffType.ON);
            } else {
                eventPublisher.postUpdate(itemName, OnOffType.OFF);
            }
        } else if (itemType.equals(ContactItem.class)) {
            if (on) {
                eventPublisher.postUpdate(itemName, OpenClosedType.CLOSED);
            } else {
                eventPublisher.postUpdate(itemName, OpenClosedType.OPEN);
            }
        } else {
            throw new IllegalStateException("invalid command type: " + itemType);
        }

    }

    class PollingThread extends Thread {
        private final byte portMask;
        private final char bank;
        private final EventPublisher eventPublisher;
        private final String itemName;
        private final Class<? extends Item> itemType;
        private final int pollInterval;

        public PollingThread(byte portMask, char bank, EventPublisher eventPublisher, String itemName,
                Class<? extends Item> itemType, int pollInterval) {
            super();
            this.portMask = portMask;
            this.bank = bank;
            this.eventPublisher = eventPublisher;
            this.itemName = itemName;
            this.itemType = itemType;
            this.pollInterval = pollInterval;
            LOG.debug("new polling thread created for item: {}", itemName);
        }

        @Override
        public void run() {
            byte registerSwitch = getRegisterSwitch(bank);

            boolean lastState = false;

            while (!isInterrupted()) {
                MCP23016Device.this.open("/dev/i2c-1");
                int read = -1;
                try {
                    read = (MCP23016Device.this.read(registerSwitch) & 0xFF);
                    LOG.trace("read value '{}' for portmask: {}", String.format("%02x", (read & 0xFF)), portMask);
                } finally {
                    MCP23016Device.this.close();
                }

                byte result = (byte) ((read & 0xFF) & portMask);
                LOG.trace("result: " + String.format("%02x", result));
                boolean newState = result == portMask;
                if (lastState != newState) {
                    if (newState) {
                        updateItem(eventPublisher, itemName, true, itemType);
                    } else {
                        updateItem(eventPublisher, itemName, false, itemType);
                    }
                    lastState = newState;
                }

                try {
                    Thread.sleep(pollInterval);
                } catch (InterruptedException e) {
                    // its ok
                }
            }
        }

    }

}
