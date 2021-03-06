package com.futurice.android.reservator.view;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.futurice.android.reservator.R;
import com.futurice.android.reservator.common.Helpers;
import com.futurice.android.reservator.model.Reservation;
import com.futurice.android.reservator.model.Room;
import com.futurice.android.reservator.model.TimeSpan;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class RoomTrafficLights extends RelativeLayout {
    private static Date lastTimeConnected = new Date(0);
    final long TOUCH_TIMEOUT = 30 * 1000;
    final long TOUCH_TIMER = 10 * 1000;
    final int QUICK_BOOK_THRESHOLD = 5; // minutes
    // Show "disconnected" warning icon on screen when disconnected for more than 5 minutes
    private final long DISCONNECTED_WARNING_ICON_THRESHOLD = 5 * 60 * 1000;
    TextView roomStatusView;
    TextView roomTitleView;
    TextView roomStatusInfoView;
    TextView reservationInfoView;
    private TextView followingMeeting;
    Button bookView, bookNowButton;
    View disconnected;
    Timer touchTimeoutTimer;
    boolean enabled = true;
    View.OnClickListener bookListener;
    private long lastTouched = 0;
    private OnClickListener bookNowListener;

    public RoomTrafficLights(Context context) {
        this(context, null);
    }

    public RoomTrafficLights(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflate(context, R.layout.room_traffic_lights, this);
        roomTitleView = (TextView) findViewById(R.id.roomTitle);
        roomStatusView = (TextView) findViewById(R.id.roomStatus);
        roomStatusInfoView = (TextView) findViewById(R.id.roomStatusInfo);
        reservationInfoView = (TextView) findViewById(R.id.reservationInfo);
        bookView = (Button) findViewById(R.id.book);
        bookNowButton = (Button) findViewById(R.id.bookNow);
        followingMeeting = (TextView) findViewById(R.id.followMeeting);

        disconnected = findViewById(R.id.disconnected);
        updateConnected();

        setClickable(true);
        setVisibility(INVISIBLE);

        bookView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bookListener != null && bookView.getVisibility() == VISIBLE) {
                    bookListener.onClick(v);
                }
            }
        });

        bookNowButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bookNowListener != null && bookNowButton.getVisibility() == VISIBLE){
                    bookNowListener.onClick(v);
                }
            }
        });
    }

    public void setBookListener(OnClickListener l) {
        this.bookListener = l;
    }

    public void setBookNowListener(OnClickListener l) {
        this.bookNowListener = l;
    }

    public void update(Room room) {
        updateConnected();

        roomTitleView.setText(room.getShownRoomName());

        if (room.isBookable(QUICK_BOOK_THRESHOLD)) {
            roomStatusView.setText(this.getContext().getString(R.string.free));
            bookNowButton.setVisibility(VISIBLE);
            followingMeeting.setVisibility(GONE);

            if (room.isFreeRestOfDay()) {
                roomStatusInfoView.setText(this.getContext().getString(R.string.freeDay));
                this.setBackgroundColor(getResources().getColor(R.color.TrafficLightFree));
                // Must use deprecated API for some reason or it crashes on older tablets
                setBookButtonGreen(bookView);
                setBookButtonGreen(bookNowButton);
            } else {
                int freeMinutes = room.minutesFreeFromNow();
                roomStatusView.setText(this.getContext().getString(R.string.free));
                String statusText = String.format("%s %s", this.getContext().getString(R.string.forX),Helpers.humanizeTimeSpan2(freeMinutes, this.getContext()));
                roomStatusInfoView.setText(statusText);

                if (freeMinutes >= 2*Room.RESERVED_THRESHOLD_MINUTES){
                    this.setBackgroundColor(getResources().getColor(R.color.TrafficLightFree));
                    setBookButtonGreen(bookView);
                    setBookButtonGreen(bookNowButton);
                }else {
                    bookNowButton.setVisibility(GONE);
                    if (freeMinutes >= Room.RESERVED_THRESHOLD_MINUTES) {
                        this.setBackgroundColor(getResources().getColor(R.color.TrafficLightFree));
                        setBookButtonGreen(bookView);
                    } else {
                        this.setBackgroundColor(getResources().getColor(R.color.TrafficLightYellow));
                        bookView.setBackgroundDrawable(getResources().getDrawable(R.drawable.traffic_lights_button_yellow));
                        bookView.setTextColor(getResources().getColorStateList(R.color.traffic_lights_button_yellow));
                    }
                }
            }
            reservationInfoView.setVisibility(GONE);
            roomStatusInfoView.setVisibility(VISIBLE);
            bookView.setVisibility(VISIBLE);
        } else {
            this.setBackgroundColor(getResources().getColor(R.color.TrafficLightReserved));
            roomStatusView.setText(this.getContext().getString(R.string.defaultTitleForReservation));
            bookView.setVisibility(GONE);
            bookNowButton.setVisibility(GONE);
            setFollowMeeting(room);
            setReservationInfo(room.getCurrentReservation(), room);
        }
    }

    private void setBookButtonGreen(Button button) {
        button.setBackgroundDrawable(getResources().getDrawable(R.drawable.traffic_lights_button_green));
        button.setTextColor(getResources().getColorStateList(R.color.traffic_lights_button_green));
    }

    private void updateConnected() {

        if(isInEditMode()){
            disconnected.setVisibility(GONE);
            return;
        }

        ConnectivityManager cm = null;
        try {
            cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        } catch (ClassCastException cce) {
            return;
        }
        if (cm == null) return;

        NetworkInfo ni = cm.getActiveNetworkInfo();

        if (ni != null && ni.isConnectedOrConnecting()) {
            // Connected
            lastTimeConnected = new Date();
            if (disconnected.getVisibility() != GONE) {
                disconnected.setVisibility(GONE);
            }
        } else {
            // Disconnected
            if (lastTimeConnected.before(new Date(new Date().getTime() - DISCONNECTED_WARNING_ICON_THRESHOLD))) {
                if (disconnected.getVisibility() != VISIBLE) {
                    disconnected.setVisibility(VISIBLE);
                }
            }
        }
    }

    private void setReservationInfo(Reservation r, Room room) {
        TimeSpan nextFreeSlot = room.getNextFreeSlot();
        if (r == null) {
            roomStatusInfoView.setVisibility(GONE);
        } else {
            roomStatusInfoView.setText(r.getSubject());
            roomStatusInfoView.setVisibility(VISIBLE);
        }

        if (nextFreeSlot == null) {
            // More than MakeReservationTask day away
            reservationInfoView.setVisibility(GONE);
        } else {
            String text = String.format(Locale.getDefault(),"%02d:%02d",nextFreeSlot.getStart().get(Calendar.HOUR_OF_DAY), nextFreeSlot.getStart().get(Calendar.MINUTE));
            reservationInfoView.setText(String.format("%s %s %s ", getContext().getString(R.string.freeAt),text, getLastingTimeReservation(room)));
            reservationInfoView.setVisibility(VISIBLE);
        }
    }

    private String getLastingTimeReservation (Room room) {
        long diffHours = room.getTimeDifferenceHour(lastTimeConnected);
        long diffMinutes = room.getTimeDifferenceMinute(lastTimeConnected);
        diffMinutes += diffHours *60;
            return String.format("%s %s%s", "("+getContext().getString(R.string.freeIn), Helpers.humanizeTimeSpan2((int)diffMinutes,this.getContext()),")");

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean capture = false;
        if (getVisibility() == VISIBLE) {
            setVisibility(INVISIBLE);
            capture = true;
            if (enabled) scheduleTimer();
        }
        lastTouched = new Date().getTime();
        return capture;
    }

    public void disable() {
        enabled = false;
        setVisibility(INVISIBLE);
        lastTouched = new Date().getTime();
        descheduleTimer();
    }

    public void enable() {
        enabled = true;
        lastTouched = new Date().getTime();
        scheduleTimer();
    }

    private void scheduleTimer() {
        if (touchTimeoutTimer == null) {
            touchTimeoutTimer = new Timer();
            touchTimeoutTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (RoomTrafficLights.this.enabled &&
                        new Date().getTime() >= RoomTrafficLights.this.lastTouched + TOUCH_TIMEOUT) {
                        RoomTrafficLights.this.post(new Runnable() {
                            public void run() {
                                RoomTrafficLights.this.setVisibility(VISIBLE);
                            }
                        });
                        descheduleTimer();
                    }
                }
            }, TOUCH_TIMER, TOUCH_TIMEOUT);
        }
    }

    private void descheduleTimer() {
        if (touchTimeoutTimer != null) {
            touchTimeoutTimer.cancel();
            touchTimeoutTimer = null;
        }
    }

    private void setFollowMeeting(Room room) {
        Reservation followingReservation = room.getFollowingReservation();
        if (followingReservation != null) {


            String meetingName = followingReservation.getSubject();
            followingMeeting.setText(getContext().getString(R.string.FollowMeeting) +" "+meetingName);
            followingMeeting.setVisibility(VISIBLE);
        }
    }
}
