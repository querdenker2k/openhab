package org.openhab.binding.gpio_mcp23017.internal;

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

public class MCP23017Device extends I2CDevice<MCP23017Config, MCP23017ItemConfig> {
    private static final Logger LOG = LoggerFactory.getLogger(MCP23017Device.class);

    private ExecutorService executors = Executors.newCachedThreadPool();
    private Map<String, PollingThread> pollingMap = new HashMap<String, PollingThread>();
    private List<String> directionSetList = new ArrayList<String>();

    public MCP23017Device(MCP23017Config config) {
        super(config);
    }

    public void startPolling(final MCP23017ItemConfig itemConfig, final EventPublisher eventPublisher) {
        final byte portMask = (byte) (0x01 << itemConfig.getPort());
        this.setDirection(portMask, itemConfig.getPort(), itemConfig.getBank(), true);

        String key = itemConfig.getId() + itemConfig.getBank();
        if (!pollingMap.containsKey(key)) {
            LOG.debug("starting new thread for id={}, address={}, pollInterval={}", key, getConfig().getAddress(),
                    getConfig().getPollInterval());
            PollingThread run = new PollingThread(itemConfig.getBank(), eventPublisher, getConfig().getPollInterval());
            this.pollingMap.put(key, run);
            this.executors.execute(run);
        }

        PollingThread run = pollingMap.get(key);
        run.addPollingData(new PollingData(portMask, itemConfig.getItem().getName(), itemConfig.getItem().getClass(),
                itemConfig.isActiveLow()));
    }

    public void stopPolling() {
        this.pollingMap.clear();
        this.executors.shutdownNow();
    }

    @Override
    public State communicate(Command command, final MCP23017ItemConfig itemConfig, State state) {
        if (command instanceof OnOffType) {
            OnOffType onOffType = (OnOffType) command;
            switch (onOffType) {
                case ON:
                    change(this.getConfig().getAddress(), itemConfig.getPort(), itemConfig.getBank(), true,
                            itemConfig.isActiveLow());
                    break;
                case OFF:
                    change(this.getConfig().getAddress(), itemConfig.getPort(), itemConfig.getBank(), false,
                            itemConfig.isActiveLow());
                    break;
                default:
                    throw new IllegalStateException("not implemented state: " + onOffType);
            }
            return null;
        } else if (command instanceof OpenClosedType) {
            OpenClosedType openClosedType = (OpenClosedType) command;
            switch (openClosedType) {
                case OPEN:
                    change(this.getConfig().getAddress(), itemConfig.getPort(), itemConfig.getBank(), true,
                            itemConfig.isActiveLow());
                    break;
                case CLOSED:
                    change(this.getConfig().getAddress(), itemConfig.getPort(), itemConfig.getBank(), false,
                            itemConfig.isActiveLow());
                    break;
                default:
                    throw new IllegalStateException("not implemented state: " + openClosedType);
            }
            return null;
        } else {
            throw new IllegalStateException("not implemented command-type: " + command);
        }
    }

    private void change(byte address, byte port, char bank, boolean on, boolean activeLow) {

        byte portAddress = (byte) (0x01 << port);
        byte registerSwitch = getRegisterSwitch(bank);
        byte registerDir = getRegisterDir(bank);

        LOG.debug("switch address={}, port={}, registerDir={}, registerPort={}",
                new Object[] { Integer.toHexString(address), Integer.toHexString(portAddress),
                        Integer.toHexString(registerDir), Integer.toHexString(registerSwitch) });

        this.setDirection(portAddress, port, bank, false);

        if (activeLow) {
            on = !on;
        }

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
            return 0x12;
        } else if (bank == 'b') {
            return 0x13;
        } else {
            throw new IllegalStateException("unknown bank: " + bank);
        }
    }

    private static byte getRegisterDir(char bank) {
        if (bank == 'a') {
            return 0x00;
        } else if (bank == 'b') {
            return 0x01;
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
            byte oldValue = (byte) (super.read(registerDir) & 0xFF);
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
                directionSetList.add(bank + "" + port);
                return;
            }
            boolean res = super.write(registerDir, newValue);
            if (!res) {
                throw new IllegalStateException(
                        String.format("cannot set port as %s, error='%s'", in == true ? "input" : "output", res));
            }
            directionSetList.add(bank + "" + port);
        } finally {
            super.close();
        }
    }

    private void read(byte address, byte portMask, char bank, EventPublisher eventPublisher, String itemName,
            Class<? extends Item> itemType) {

    }

    private static void updateItem(EventPublisher eventPublisher, String itemName, boolean on,
            Class<? extends Item> itemType) {
        if (itemType.equals(SwitchItem.class)) {
            if (on) {
                eventPublisher.sendCommand(itemName, OnOffType.ON);
            } else {
                eventPublisher.sendCommand(itemName, OnOffType.OFF);
            }
        } else if (itemType.equals(ContactItem.class)) {
            if (on) {
                eventPublisher.sendCommand(itemName, OpenClosedType.CLOSED);
            } else {
                eventPublisher.sendCommand(itemName, OpenClosedType.OPEN);
            }
        } else {
            throw new IllegalStateException("invalid command type: " + itemType);
        }

    }

    class PollingData {
        private final byte portMask;
        private final String itemName;
        private final Class<? extends Item> itemType;
        private final boolean activeLow;

        public PollingData(byte portMask, String itemName, Class<? extends Item> itemType, boolean activeLow) {
            super();
            this.portMask = portMask;
            this.itemName = itemName;
            this.itemType = itemType;
            this.activeLow = activeLow;
        }
    }

    class PollingThread extends Thread {
        private final char bank;
        private final EventPublisher eventPublisher;
        private final int pollInterval;
        private Map<String, PollingData> pollingDataMap = new HashMap<>();

        public PollingThread(char bank, EventPublisher eventPublisher, int pollInterval) {
            super();
            this.bank = bank;
            this.eventPublisher = eventPublisher;
            this.pollInterval = pollInterval;
            LOG.debug("new polling thread created for bank: {}", bank);
        }

        public void addPollingData(PollingData pollingData) {
            if (!this.pollingDataMap.containsKey(pollingData.itemName)) {
                LOG.debug("adding polling for data: {}", pollingData);
                this.pollingDataMap.put(pollingData.itemName, pollingData);
            }
        }

        @Override
        public void run() {
            try {
                byte registerSwitch = getRegisterSwitch(bank);
                Map<Byte, Boolean> lastStateMap = new HashMap<>();
                while (!isInterrupted()) {
                    MCP23017Device.this.open("/dev/i2c-1");
                    int read = -1;
                    try {
                        read = (MCP23017Device.this.read(registerSwitch) & 0xFF);
                        LOG.trace("read value '{}'", String.format("%02x", (read & 0xFF)));
                    } finally {
                        MCP23017Device.this.close();
                    }

                    for (PollingData pollingData : new ArrayList<>(pollingDataMap.values())) {
                        byte result = (byte) ((read & 0xFF) & pollingData.portMask);
                        boolean newState = result == pollingData.portMask;
                        LOG.trace("result for pin ({}): {} -> {}", pollingData.portMask, String.format("%02x", result),
                                newState);

                        Boolean lastState = lastStateMap.get(pollingData.portMask);
                        if (lastState == null) {
                            lastState = !newState;
                        }

                        if (lastState != newState) {
                            boolean stateToSet = newState;

                            if (pollingData.activeLow) {
                                stateToSet = !stateToSet;
                            }
                            LOG.debug("setting pin ({}): {} (activeLow={})", pollingData.portMask, stateToSet,
                                    pollingData.activeLow);
                            updateItem(eventPublisher, pollingData.itemName, stateToSet, pollingData.itemType);

                            lastStateMap.put(pollingData.portMask, newState);
                        }
                    }

                    try {
                        Thread.sleep(pollInterval);
                    } catch (InterruptedException e) {
                        // its ok
                    }
                }
            } catch (Exception e) {
                LOG.error("error reading pin", e);
            }
        }
    }

}
