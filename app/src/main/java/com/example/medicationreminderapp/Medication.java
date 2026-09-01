package com.example.medicationreminderapp;

public class Medication {
    private int id;
    private String name;
    private String dosage;
    private String time;
    private int hour;
    private int minute;
    private boolean isTaken;
    private long lastTaken;

    public Medication(int id, String name, String dosage, String time, int hour, int minute, boolean isTaken, long lastTaken) {
        this.id = id;
        this.name = name;
        this.dosage = dosage;
        this.time = time;
        this.hour = hour;
        this.minute = minute;
        this.isTaken = isTaken;
        this.lastTaken = lastTaken;
    }

    // Constructor without ID for creating new medications before DB insertion
    public Medication(String name, String dosage, String time, int hour, int minute, boolean isTaken) {
        this.name = name;
        this.dosage = dosage;
        this.time = time;
        this.hour = hour;
        this.minute = minute;
        this.isTaken = isTaken;
        this.lastTaken = 0;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public String getDosage() { return dosage; }
    public String getTime() { return time; }
    public int getHour() { return hour; }
    public int getMinute() { return minute; }
    public boolean isTaken() { return isTaken; }
    public void setTaken(boolean taken) { isTaken = taken; }
    public long getLastTaken() { return lastTaken; }
    public void setLastTaken(long lastTaken) { this.lastTaken = lastTaken; }
}