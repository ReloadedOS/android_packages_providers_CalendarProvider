/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.providers.calendar;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.provider.Calendar;
import android.provider.ContactsContract;
import android.util.Log;
import com.android.internal.content.SyncStateContentProviderHelper;

/**
 * Database helper for calendar. Designed as a singleton to make sure that all
 * {@link android.content.ContentProvider} users get the same reference.
 */
/* package */ class CalendarDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "CalendarDatabaseHelper";

    private static final String DATABASE_NAME = "calendar2.db";

    // TODO: change the Calendar contract so these are defined there.
    static final String ACCOUNT_NAME = "_sync_account";
    static final String ACCOUNT_TYPE = "_sync_account_type";

    // Note: if you update the version number, you must also update the code
    // in upgradeDatabase() to modify the database (gracefully, if possible).
    private static final int DATABASE_VERSION = 57;

    private final Context mContext;
    private final SyncStateContentProviderHelper mSyncState;

    private static CalendarDatabaseHelper sSingleton = null;

    private DatabaseUtils.InsertHelper mCalendarsInserter;
    private DatabaseUtils.InsertHelper mEventsInserter;
    private DatabaseUtils.InsertHelper mEventsRawTimesInserter;
    private DatabaseUtils.InsertHelper mDeletedEventsInserter;
    private DatabaseUtils.InsertHelper mInstancesInserter;
    private DatabaseUtils.InsertHelper mAttendeesInserter;
    private DatabaseUtils.InsertHelper mRemindersInserter;
    private DatabaseUtils.InsertHelper mCalendarAlertsInserter;
    private DatabaseUtils.InsertHelper mExtendedPropertiesInserter;

    public long calendarsInsert(ContentValues values) {
        return mCalendarsInserter.insert(values);
    }

    public long eventsInsert(ContentValues values) {
        return mEventsInserter.insert(values);
    }

    public long eventsRawTimesInsert(ContentValues values) {
        return mEventsRawTimesInserter.insert(values);
    }

    public long eventsRawTimesReplace(ContentValues values) {
        return mEventsRawTimesInserter.replace(values);
    }

    public long deletedEventsInsert(ContentValues values) {
        return mDeletedEventsInserter.insert(values);
    }

    public long instancesInsert(ContentValues values) {
        return mInstancesInserter.insert(values);
    }

    public long attendeesInsert(ContentValues values) {
        return mAttendeesInserter.insert(values);
    }

    public long remindersInsert(ContentValues values) {
        return mRemindersInserter.insert(values);
    }

    public long calendarAlertsInsert(ContentValues values) {
        return mCalendarAlertsInserter.insert(values);
    }

    public long extendedPropertiesInsert(ContentValues values) {
        return mExtendedPropertiesInserter.insert(values);
    }

    public static synchronized CalendarDatabaseHelper getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new CalendarDatabaseHelper(context);
        }
        return sSingleton;
    }

    /**
     * Private constructor, callers except unit tests should obtain an instance through
     * {@link #getInstance(android.content.Context)} instead.
     */
    CalendarDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        if (false) Log.i(TAG, "Creating OpenHelper");
        Resources resources = context.getResources();

        mContext = context;
        mSyncState = new SyncStateContentProviderHelper();
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        mSyncState.onDatabaseOpened(db);
        db.markTableSyncable("Events", "DeletedEvents");

        mCalendarsInserter = new DatabaseUtils.InsertHelper(db, "Calendars");
        mEventsInserter = new DatabaseUtils.InsertHelper(db, "Events");
        mEventsRawTimesInserter = new DatabaseUtils.InsertHelper(db, "EventsRawTimes");
        mDeletedEventsInserter = new DatabaseUtils.InsertHelper(db, "DeletedEvents");
        mInstancesInserter = new DatabaseUtils.InsertHelper(db, "Instances");
        mAttendeesInserter = new DatabaseUtils.InsertHelper(db, "Attendees");
        mRemindersInserter = new DatabaseUtils.InsertHelper(db, "Reminders");
        mCalendarAlertsInserter = new DatabaseUtils.InsertHelper(db, "CalendarAlerts");
        mExtendedPropertiesInserter =
                new DatabaseUtils.InsertHelper(db, "ExtendedProperties");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Bootstrapping database");

        mSyncState.createDatabase(db);

        db.execSQL("CREATE TABLE Calendars (" +
                "_id INTEGER PRIMARY KEY," +
                ACCOUNT_NAME + " TEXT," +
                ACCOUNT_TYPE + " TEXT," +
                "_sync_id TEXT," +
                "_sync_version TEXT," +
                "_sync_time TEXT," +            // UTC
                "_sync_local_id INTEGER," +
                "_sync_dirty INTEGER," +
                "_sync_mark INTEGER," + // Used to filter out new rows
                "url TEXT," +
                "name TEXT," +
                "displayName TEXT," +
                "hidden INTEGER NOT NULL DEFAULT 0," +
                "color INTEGER," +
                "access_level INTEGER," +
                "selected INTEGER NOT NULL DEFAULT 1," +
                "sync_events INTEGER NOT NULL DEFAULT 0," +
                "location TEXT," +
                "timezone TEXT," +
                "ownerAccount TEXT" +
                ");");

        // Trigger to remove a calendar's events when we delete the calendar
        db.execSQL("CREATE TRIGGER calendar_cleanup DELETE ON Calendars " +
                "BEGIN " +
                "DELETE FROM Events WHERE calendar_id = old._id;" +
                "DELETE FROM DeletedEvents WHERE calendar_id = old._id;" +
                "END");

        // TODO: do we need both dtend and duration?
        db.execSQL("CREATE TABLE Events (" +
                "_id INTEGER PRIMARY KEY," +
                ACCOUNT_NAME + " TEXT," +
                ACCOUNT_TYPE + " TEXT," +
                "_sync_id TEXT," +
                "_sync_version TEXT," +
                "_sync_time TEXT," +            // UTC
                "_sync_local_id INTEGER," +
                "_sync_dirty INTEGER," +
                "_sync_mark INTEGER," + // To filter out new rows
                "calendar_id INTEGER NOT NULL," +
                "htmlUri TEXT," +
                "title TEXT," +
                "eventLocation TEXT," +
                "description TEXT," +
                "eventStatus INTEGER," +
                "selfAttendeeStatus INTEGER NOT NULL DEFAULT 0," +
                "commentsUri TEXT," +
                "dtstart INTEGER," +               // millis since epoch
                "dtend INTEGER," +                 // millis since epoch
                "eventTimezone TEXT," +         // timezone for event
                "duration TEXT," +
                "allDay INTEGER NOT NULL DEFAULT 0," +
                "visibility INTEGER NOT NULL DEFAULT 0," +
                "transparency INTEGER NOT NULL DEFAULT 0," +
                "hasAlarm INTEGER NOT NULL DEFAULT 0," +
                "hasExtendedProperties INTEGER NOT NULL DEFAULT 0," +
                "rrule TEXT," +
                "rdate TEXT," +
                "exrule TEXT," +
                "exdate TEXT," +
                "originalEvent TEXT," +  // _sync_id of recurring event
                "originalInstanceTime INTEGER," +  // millis since epoch
                "originalAllDay INTEGER," +
                "lastDate INTEGER," +               // millis since epoch
                "hasAttendeeData INTEGER NOT NULL DEFAULT 0," +
                "guestsCanModify INTEGER NOT NULL DEFAULT 0," +
                "guestsCanInviteOthers INTEGER NOT NULL DEFAULT 1," +
                "guestsCanSeeGuests INTEGER NOT NULL DEFAULT 1," +
                "organizer STRING" +
                ");");

        db.execSQL("CREATE INDEX eventSyncAccountAndIdIndex ON Events ("
                + Calendar.Events._SYNC_ACCOUNT_TYPE + ", " + Calendar.Events._SYNC_ACCOUNT + ", "
                + Calendar.Events._SYNC_ID + ");");

        db.execSQL("CREATE INDEX eventsCalendarIdIndex ON Events (" +
                Calendar.Events.CALENDAR_ID +
                ");");

        db.execSQL("CREATE TABLE EventsRawTimes (" +
                "_id INTEGER PRIMARY KEY," +
                "event_id INTEGER NOT NULL," +
                "dtstart2445 TEXT," +
                "dtend2445 TEXT," +
                "originalInstanceTime2445 TEXT," +
                "lastDate2445 TEXT," +
                "UNIQUE (event_id)" +
                ");");

        // NOTE: we do not create a trigger to delete an event's instances upon update,
        // as all rows currently get updated during a merge.

        db.execSQL("CREATE TABLE DeletedEvents (" +
                "_sync_id TEXT," +
                "_sync_version TEXT," +
                ACCOUNT_NAME + " TEXT," +
                ACCOUNT_TYPE + " TEXT," +
                "_sync_mark INTEGER," + // To filter out new rows
                "calendar_id INTEGER" +
                ");");

        db.execSQL("CREATE TABLE Instances (" +
                "_id INTEGER PRIMARY KEY," +
                "event_id INTEGER," +
                "begin INTEGER," +         // UTC millis
                "end INTEGER," +           // UTC millis
                "startDay INTEGER," +      // Julian start day
                "endDay INTEGER," +        // Julian end day
                "startMinute INTEGER," +   // minutes from midnight
                "endMinute INTEGER," +     // minutes from midnight
                "UNIQUE (event_id, begin, end)" +
                ");");

        db.execSQL("CREATE INDEX instancesStartDayIndex ON Instances (" +
                Calendar.Instances.START_DAY +
                ");");

        db.execSQL("CREATE TABLE CalendarMetaData (" +
                "_id INTEGER PRIMARY KEY," +
                "localTimezone TEXT," +
                "minInstance INTEGER," +      // UTC millis
                "maxInstance INTEGER," +      // UTC millis
                "minBusyBits INTEGER," +      // UTC millis
                "maxBusyBits INTEGER" +       // UTC millis
                ");");

        db.execSQL("CREATE TABLE BusyBits(" +
                "day INTEGER PRIMARY KEY," +  // the Julian day
                "busyBits INTEGER," +         // 24 bits for 60-minute intervals
                "allDayCount INTEGER" +       // number of all-day events
                ");");

        db.execSQL("CREATE TABLE Attendees (" +
                "_id INTEGER PRIMARY KEY," +
                "event_id INTEGER," +
                "attendeeName TEXT," +
                "attendeeEmail TEXT," +
                "attendeeStatus INTEGER," +
                "attendeeRelationship INTEGER," +
                "attendeeType INTEGER" +
                ");");

        db.execSQL("CREATE INDEX attendeesEventIdIndex ON Attendees (" +
                Calendar.Attendees.EVENT_ID +
                ");");

        db.execSQL("CREATE TABLE Reminders (" +
                "_id INTEGER PRIMARY KEY," +
                "event_id INTEGER," +
                "minutes INTEGER," +
                "method INTEGER NOT NULL" +
                " DEFAULT " + Calendar.Reminders.METHOD_DEFAULT +
                ");");

        db.execSQL("CREATE INDEX remindersEventIdIndex ON Reminders (" +
                Calendar.Reminders.EVENT_ID +
                ");");

        // This table stores the Calendar notifications that have gone off.
        db.execSQL("CREATE TABLE CalendarAlerts (" +
                "_id INTEGER PRIMARY KEY," +
                "event_id INTEGER," +
                "begin INTEGER NOT NULL," +         // UTC millis
                "end INTEGER NOT NULL," +           // UTC millis
                "alarmTime INTEGER NOT NULL," +     // UTC millis
                "creationTime INTEGER NOT NULL," +  // UTC millis
                "receivedTime INTEGER NOT NULL," +  // UTC millis
                "notifyTime INTEGER NOT NULL," +    // UTC millis
                "state INTEGER NOT NULL," +
                "minutes INTEGER," +
                "UNIQUE (alarmTime, begin, event_id)" +
                ");");

        db.execSQL("CREATE INDEX calendarAlertsEventIdIndex ON CalendarAlerts (" +
                Calendar.CalendarAlerts.EVENT_ID +
                ");");

        db.execSQL("CREATE TABLE ExtendedProperties (" +
                "_id INTEGER PRIMARY KEY," +
                "event_id INTEGER," +
                "name TEXT," +
                "value TEXT" +
                ");");

        db.execSQL("CREATE INDEX extendedPropertiesEventIdIndex ON ExtendedProperties (" +
                Calendar.ExtendedProperties.EVENT_ID +
                ");");

        // Trigger to remove data tied to an event when we delete that event.
        db.execSQL("CREATE TRIGGER events_cleanup_delete DELETE ON Events " +
                "BEGIN " +
                "DELETE FROM Instances WHERE event_id = old._id;" +
                "DELETE FROM EventsRawTimes WHERE event_id = old._id;" +
                "DELETE FROM Attendees WHERE event_id = old._id;" +
                "DELETE FROM Reminders WHERE event_id = old._id;" +
                "DELETE FROM CalendarAlerts WHERE event_id = old._id;" +
                "DELETE FROM ExtendedProperties WHERE event_id = old._id;" +
                "END");

        // Triggers to set the _sync_dirty flag when an attendee is changed,
        // inserted or deleted
        db.execSQL("CREATE TRIGGER attendees_update UPDATE ON Attendees " +
                "BEGIN " +
                "UPDATE Events SET _sync_dirty=1 WHERE Events._id=old.event_id;" +
                "END");
        db.execSQL("CREATE TRIGGER attendees_insert INSERT ON Attendees " +
                "BEGIN " +
                "UPDATE Events SET _sync_dirty=1 WHERE Events._id=new.event_id;" +
                "END");
        db.execSQL("CREATE TRIGGER attendees_delete DELETE ON Attendees " +
                "BEGIN " +
                "UPDATE Events SET _sync_dirty=1 WHERE Events._id=old.event_id;" +
                "END");

        // Triggers to set the _sync_dirty flag when a reminder is changed,
        // inserted or deleted
        db.execSQL("CREATE TRIGGER reminders_update UPDATE ON Reminders " +
                "BEGIN " +
                "UPDATE Events SET _sync_dirty=1 WHERE Events._id=old.event_id;" +
                "END");
        db.execSQL("CREATE TRIGGER reminders_insert INSERT ON Reminders " +
                "BEGIN " +
                "UPDATE Events SET _sync_dirty=1 WHERE Events._id=new.event_id;" +
                "END");
        db.execSQL("CREATE TRIGGER reminders_delete DELETE ON Reminders " +
                "BEGIN " +
                "UPDATE Events SET _sync_dirty=1 WHERE Events._id=old.event_id;" +
                "END");
        // Triggers to set the _sync_dirty flag when an extended property is changed,
        // inserted or deleted
        db.execSQL("CREATE TRIGGER extended_properties_update UPDATE ON ExtendedProperties " +
                "BEGIN " +
                "UPDATE Events SET _sync_dirty=1 WHERE Events._id=old.event_id;" +
                "END");
        db.execSQL("CREATE TRIGGER extended_properties_insert UPDATE ON ExtendedProperties " +
                "BEGIN " +
                "UPDATE Events SET _sync_dirty=1 WHERE Events._id=new.event_id;" +
                "END");
        db.execSQL("CREATE TRIGGER extended_properties_delete UPDATE ON ExtendedProperties " +
                "BEGIN " +
                "UPDATE Events SET _sync_dirty=1 WHERE Events._id=old.event_id;" +
                "END");

        ContentResolver.requestSync(null /* all accounts */,
                ContactsContract.AUTHORITY, new Bundle());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading DB from version " + oldVersion
                + " to " + newVersion);
        if (oldVersion < 46) {
            dropTables(db);
            mSyncState.createDatabase(db);
            return; // this was lossy
        }

        if (oldVersion == 46) {
            Log.w(TAG, "Upgrading CalendarAlerts table");
            db.execSQL("UPDATE CalendarAlerts SET reminder_id=NULL;");
            db.execSQL("ALTER TABLE CalendarAlerts ADD COLUMN minutes INTEGER DEFAULT 0;");
            oldVersion += 1;
        }

        if (oldVersion == 47) {
            // Changing to version 48 was intended to force a data wipe
            dropTables(db);
            mSyncState.createDatabase(db);
            return; // this was lossy
        }

        if (oldVersion == 48) {
            // Changing to version 49 was intended to force a data wipe
            dropTables(db);
            mSyncState.createDatabase(db);
            return; // this was lossy
        }

        if (oldVersion == 49) {
            Log.w(TAG, "Upgrading DeletedEvents table");

            // We don't have enough information to fill in the correct
            // value of the calendar_id for old rows in the DeletedEvents
            // table, but rows in that table are transient so it is unlikely
            // that there are any rows.  Plus, the calendar_id is used only
            // when deleting a calendar, which is a rare event.  All new rows
            // will have the correct calendar_id.
            db.execSQL("ALTER TABLE DeletedEvents ADD COLUMN calendar_id INTEGER;");

            // Trigger to remove a calendar's events when we delete the calendar
            db.execSQL("DROP TRIGGER IF EXISTS calendar_cleanup");
            db.execSQL("CREATE TRIGGER calendar_cleanup DELETE ON Calendars " +
                    "BEGIN " +
                    "DELETE FROM Events WHERE calendar_id = old._id;" +
                    "DELETE FROM DeletedEvents WHERE calendar_id = old._id;" +
                    "END");
            db.execSQL("DROP TRIGGER IF EXISTS event_to_deleted");
            oldVersion += 1;
        }

        if (oldVersion == 50) {
            // This should have been deleted in the upgrade from version 49
            // but we missed it.
            db.execSQL("DROP TRIGGER IF EXISTS event_to_deleted");
            oldVersion += 1;
        }

        if (oldVersion == 51) {
            // We added "originalAllDay" to the Events table to keep track of
            // the allDay status of the original recurring event for entries
            // that are exceptions to that recurring event.  We need this so
            // that we can format the date correctly for the "originalInstanceTime"
            // column when we make a change to the recurrence exception and
            // send it to the server.
            db.execSQL("ALTER TABLE Events ADD COLUMN originalAllDay INTEGER;");

            // Iterate through the Events table and for each recurrence
            // exception, fill in the correct value for "originalAllDay",
            // if possible.  The only times where this might not be possible
            // are (1) the original recurring event no longer exists, or
            // (2) the original recurring event does not yet have a _sync_id
            // because it was created on the phone and hasn't been synced to the
            // server yet.  In both cases the originalAllDay field will be set
            // to null.  In the first case we don't care because the recurrence
            // exception will not be displayed and we won't be able to make
            // any changes to it (and even if we did, the server should ignore
            // them, right?).  In the second case, the calendar client already
            // disallows making changes to an instance of a recurring event
            // until the recurring event has been synced to the server so the
            // second case should never occur.

            // "cursor" iterates over all the recurrences exceptions.
            Cursor cursor = db.rawQuery("SELECT _id,originalEvent FROM Events"
                    + " WHERE originalEvent IS NOT NULL", null /* selection args */);
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(0);
                        String originalEvent = cursor.getString(1);

                        // Find the original recurring event (if it exists)
                        Cursor recur = db.rawQuery("SELECT allDay FROM Events"
                                + " WHERE _sync_id=?", new String[] {originalEvent});
                        if (recur == null) {
                            continue;
                        }

                        try {
                            // Fill in the "originalAllDay" field of the
                            // recurrence exception with the "allDay" value
                            // from the recurring event.
                            if (recur.moveToNext()) {
                                int allDay = recur.getInt(0);
                                db.execSQL("UPDATE Events SET originalAllDay=" + allDay
                                        + " WHERE _id="+id);
                            }
                        } finally {
                            recur.close();
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
            oldVersion += 1;
        }

        if (oldVersion == 52) {
            Log.w(TAG, "Upgrading CalendarAlerts table");
            db.execSQL("ALTER TABLE CalendarAlerts ADD COLUMN creationTime INTEGER DEFAULT 0;");
            db.execSQL("ALTER TABLE CalendarAlerts ADD COLUMN receivedTime INTEGER DEFAULT 0;");
            db.execSQL("ALTER TABLE CalendarAlerts ADD COLUMN notifyTime INTEGER DEFAULT 0;");
            oldVersion += 1;
        }

        if (oldVersion == 53) {
            Log.w(TAG, "adding eventSyncAccountAndIdIndex");
            db.execSQL("CREATE INDEX eventSyncAccountAndIdIndex ON Events ("
                    + Calendar.Events._SYNC_ACCOUNT + ", " + Calendar.Events._SYNC_ID + ");");
            oldVersion += 1;
        }

        if (oldVersion == 54) {
            db.execSQL("ALTER TABLE Calendars ADD COLUMN _sync_account_type TEXT;");
            db.execSQL("ALTER TABLE Events ADD COLUMN _sync_account_type TEXT;");
            db.execSQL("ALTER TABLE DeletedEvents ADD COLUMN _sync_account_type TEXT;");
            db.execSQL("UPDATE Calendars"
                    + " SET _sync_account_type='com.google'"
                    + " WHERE _sync_account IS NOT NULL");
            db.execSQL("UPDATE Events"
                    + " SET _sync_account_type='com.google'"
                    + " WHERE _sync_account IS NOT NULL");
            db.execSQL("UPDATE DeletedEvents"
                    + " SET _sync_account_type='com.google'"
                    + " WHERE _sync_account IS NOT NULL");
            Log.w(TAG, "re-creating eventSyncAccountAndIdIndex");
            db.execSQL("DROP INDEX eventSyncAccountAndIdIndex");
            db.execSQL("CREATE INDEX eventSyncAccountAndIdIndex ON Events ("
                    + Calendar.Events._SYNC_ACCOUNT_TYPE + ", "
                    + Calendar.Events._SYNC_ACCOUNT + ", "
                    + Calendar.Events._SYNC_ID + ");");
            oldVersion += 1;
        }
        if (oldVersion == 55 || oldVersion == 56) {  // Both require resync
            // Delete sync state, so all records will be re-synced.
            db.execSQL("DELETE FROM _sync_state;");

            // "cursor" iterates over all the calendars
            Cursor cursor = db.rawQuery("SELECT _sync_account,_sync_account_type,url "
                    + "FROM Calendars",
                    null /* selection args */);
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        String accountName = cursor.getString(0);
                        String accountType = cursor.getString(1);
                        final Account account = new Account(accountName, accountType);
                        String calendarUrl = cursor.getString(2);
                        scheduleSync(account, false /* two-way sync */, calendarUrl);
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        if (oldVersion == 55) {
            db.execSQL("ALTER TABLE Calendars ADD COLUMN ownerAccount TEXT;");
            db.execSQL("ALTER TABLE Events ADD COLUMN hasAttendeeData INTEGER;");
            // Clear _sync_dirty to avoid a client-to-server sync that could blow away
            // server attendees.
            // Clear _sync_version to pull down the server's event (with attendees)
            // Change the URLs from full-selfattendance to full
            db.execSQL("UPDATE Events"
                    + " SET _sync_dirty=0,"
                    + " _sync_version=NULL,"
                    + " _sync_id="
                    + "REPLACE(_sync_id, '/private/full-selfattendance', '/private/full'),"
                    + " commentsUri ="
                    + "REPLACE(commentsUri, '/private/full-selfattendance', '/private/full');");
            db.execSQL("UPDATE Calendars"
                    + " SET url="
                    + "REPLACE(url, '/private/full-selfattendance', '/private/full');");

            // "cursor" iterates over all the calendars
            Cursor cursor = db.rawQuery("SELECT _id, url FROM Calendars",
                    null /* selection args */);
            // Add the owner column.
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        Long id = cursor.getLong(0);
                        String url = cursor.getString(1);
                        String owner = CalendarSyncAdapter.calendarEmailAddressFromFeedUrl(url);
                        db.execSQL("UPDATE Calendars SET ownerAccount=? WHERE _id=?",
                                new Object[] {owner, id});
                    }
                } finally {
                    cursor.close();
                }
            }
            oldVersion += 1;
        }
        if (oldVersion == 56) {
            db.execSQL("ALTER TABLE Events ADD COLUMN guestsCanModify"
                    + " INTEGER NOT NULL DEFAULT 0;");
            db.execSQL("ALTER TABLE Events ADD COLUMN guestsCanInviteOthers"
                    + " INTEGER NOT NULL DEFAULT 1;");
            db.execSQL("ALTER TABLE Events ADD COLUMN guestsCanSeeGuests"
                    + " INTEGER NOT NULL DEFAULT 1;");
            db.execSQL("ALTER TABLE Events ADD COLUMN organizer STRING;");
            db.execSQL("UPDATE Events SET organizer="
                    + "(SELECT attendeeEmail FROM Attendees WHERE "
                    + "Attendees.event_id = Events._id"
                    + " AND Attendees.attendeeRelationship=2);");
            oldVersion += 1;
        }
    }

    private void dropTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS Calendars;");
        db.execSQL("DROP TABLE IF EXISTS Events;");
        db.execSQL("DROP TABLE IF EXISTS EventsRawTimes;");
        db.execSQL("DROP TABLE IF EXISTS DeletedEvents;");
        db.execSQL("DROP TABLE IF EXISTS Instances;");
        db.execSQL("DROP TABLE IF EXISTS CalendarMetaData;");
        db.execSQL("DROP TABLE IF EXISTS BusyBits;");
        db.execSQL("DROP TABLE IF EXISTS Attendees;");
        db.execSQL("DROP TABLE IF EXISTS Reminders;");
        db.execSQL("DROP TABLE IF EXISTS CalendarAlerts;");
        db.execSQL("DROP TABLE IF EXISTS ExtendedProperties;");
    }

    @Override
    public synchronized SQLiteDatabase getWritableDatabase() {
        SQLiteDatabase db = super.getWritableDatabase();
        return db;
    }

    public SyncStateContentProviderHelper getSyncState() {
        return mSyncState;
    }

    /**
     * Schedule a calendar sync for the account.
     * @param account the account for which to schedule a sync
     * @param uploadChangesOnly if set, specify that the sync should only send
     *   up local changes
     * @param url the url feed for the calendar to sync (may be null)
     */
    void scheduleSync(Account account, boolean uploadChangesOnly, String url) {
        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, uploadChangesOnly);
        if (url != null) {
            extras.putString("feed", url);
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        }
        ContentResolver.requestSync(account, Calendar.Calendars.CONTENT_URI.getAuthority(), extras);
    }

    public void wipeData() {
        SQLiteDatabase db = getWritableDatabase();

        db.execSQL("DELETE FROM Calendars;");
        db.execSQL("DELETE FROM Events;");
        db.execSQL("DELETE FROM EventsRawTimes;");
        db.execSQL("DELETE FROM DeletedEvents;");
        db.execSQL("DELETE FROM Instances;");
        db.execSQL("DELETE FROM CalendarMetaData;");
        db.execSQL("DELETE FROM BusyBits;");
        db.execSQL("DELETE FROM Attendees;");
        db.execSQL("DELETE FROM Reminders;");
        db.execSQL("DELETE FROM CalendarAlerts;");
        db.execSQL("DELETE FROM ExtendedProperties;");
    }
}
