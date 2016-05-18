package org.openhab.io.caldav.internal.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.openhab.io.caldav.internal.CalDavConfig;

/**
 * Containing all events for a specific calendar and the config for the calendar.
 *
 * @author Robert
 *
 */
public class CalendarRuntime {
    private final ConcurrentHashMap<String, CalendarFile> calendarFileMap = new ConcurrentHashMap<String, CalendarFile>();

    private CalDavConfig config;

    public CalendarFile getCalendarFileByFilename(String filename) {
        for (CalendarFile calendarFile : calendarFileMap.values()) {
            if (calendarFile.getFilename().equals(filename)) {
                return calendarFile;
            }
        }
        return null;
    }

    public List<CalendarFile> getCalendarFiles() {
        return new ArrayList<>(this.calendarFileMap.values());
    }

    // public ConcurrentHashMap<String, EventContainer> getEventMap() {
    // return calendarFileMap;
    // }

    public CalDavConfig getConfig() {
        return config;
    }

    public void setConfig(CalDavConfig config) {
        this.config = config;
    }

    public void removeCalendarFile(String filename) {
        this.calendarFileMap.remove(filename);
    }

    public void addCalendarFile(CalendarFile calendarFile) {
        this.calendarFileMap.put(calendarFile.getFilename(), calendarFile);
    }

}
