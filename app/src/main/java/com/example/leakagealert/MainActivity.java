package com.example.leakagealert;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvDistance, tvPercentage, tvLiters, tvWarning, tvLastUpdate;
    private TextView tvFlow1, tvFlow2, tvReadings;
    private CardView warningCard;
    private DatabaseReference dbRef;

    // Notification variables
    private NotificationManagerCompat notificationManager;
    private static final String CHANNEL_ID = "leakage_alert_channel";
    private static final int NOTIFICATION_ID = 1;
    private String lastWarning = "";
    private boolean isFirstLoad = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize notification system
        createNotificationChannel();
        notificationManager = NotificationManagerCompat.from(this);

        // Initialize all UI elements
        tvDistance = findViewById(R.id.tvDistance);
        tvPercentage = findViewById(R.id.tvPercentage);
        tvLiters = findViewById(R.id.tvLiters);
        tvWarning = findViewById(R.id.tvWarning);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        tvFlow1 = findViewById(R.id.tvFlow1);
        tvFlow2 = findViewById(R.id.tvFlow2);
        tvReadings = findViewById(R.id.tvReadings);
        warningCard = findViewById(R.id.warningCard);

        // Set initial values
        tvWarning.setText("Loading...");
        warningCard.setCardBackgroundColor(Color.GRAY);

        // Firebase reference to waterflow_minutes
        dbRef = FirebaseDatabase.getInstance().getReference("waterflow_minutes");

        // Query to get the LATEST minute data
        Query latestQuery = dbRef.orderByKey().limitToLast(1);

        latestQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    // Get the latest entry
                    for (DataSnapshot minuteSnapshot : snapshot.getChildren()) {
                        updateUIFromMinuteData(minuteSnapshot);
                    }
                } else {
                    updateWarningUI("no_data", false);
                }
                isFirstLoad = false;
            }

            @Override
            public void onCancelled(DatabaseError error) {
                updateWarningUI("error", false);
            }
        });
    }

    private void updateUIFromMinuteData(DataSnapshot minuteSnapshot) {
        // Read data from Firebase
        String minuteStart = minuteSnapshot.child("minute_start_iso").getValue(String.class);
        Double avgUltrasonic = minuteSnapshot.child("avg_ultrasonic_cm").getValue(Double.class);
        Double avgFlow1 = minuteSnapshot.child("avg_flow_lpm1").getValue(Double.class);
        Double avgFlow2 = minuteSnapshot.child("avg_flow_lpm2").getValue(Double.class);
        Long readingCount = minuteSnapshot.child("reading_count").getValue(Long.class);
        String warning = minuteSnapshot.child("warning").getValue(String.class);
        Long timestamp = minuteSnapshot.child("timestamp").getValue(Long.class);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 1. Update last update time
                if (timestamp != null) {
                    SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    tvLastUpdate.setText("Last: " + format.format(new Date(timestamp * 1000)));
                } else if (minuteStart != null) {
                    tvLastUpdate.setText("Min: " + minuteStart.substring(11, 16)); // Just show HH:mm
                }

                // 2. Update tank level and distance
                if (avgUltrasonic != null) {
                    tvDistance.setText(String.format("%.1f cm", avgUltrasonic));
                    // Calculate percentage (0cm = full, 30cm = empty)
                    double percentage = Math.max(0, Math.min(100, (1 - avgUltrasonic / 30.0) * 100));
                    tvPercentage.setText(String.format("%.0f %%", percentage));
                } else {
                    tvDistance.setText("-- cm");
                    tvPercentage.setText("-- %");
                }

                // 3. Update flow rates
                if (avgFlow1 != null) {
                    tvFlow1.setText(String.format("%.1f L/min", avgFlow1));
                } else {
                    tvFlow1.setText("-- L/min");
                }

                if (avgFlow2 != null) {
                    tvFlow2.setText(String.format("%.1f L/min", avgFlow2));
                } else {
                    tvFlow2.setText("-- L/min");
                }

                // 4. Update flow difference
                if (avgFlow1 != null && avgFlow2 != null) {
                    double diff = Math.abs(avgFlow1 - avgFlow2);
                    tvLiters.setText(String.format("Flow Diff: %.2f L/min", diff));
                } else {
                    tvLiters.setText("Flow Diff: -- L/min");
                }

                // 5. Update readings count
                if (readingCount != null) {
                    tvReadings.setText(String.format("%d samples", readingCount));
                } else {
                    tvReadings.setText("-- samples");
                }

                // 6. Update warning and show notification if needed
                if (warning != null) {
                    boolean shouldNotify = !warning.equalsIgnoreCase(lastWarning) ||
                            (warning.equalsIgnoreCase("leak detected") && isFirstLoad);
                    updateWarningUI(warning, shouldNotify);
                    lastWarning = warning;
                } else {
                    updateWarningUI("normal", false);
                    lastWarning = "normal";
                }
            }
        });
    }

    private void updateWarningUI(String warning, boolean showNotification) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String displayText;
                int backgroundColor;
                String notificationTitle = "";
                String notificationText = "";
                boolean isCritical = false;

                switch (warning.toLowerCase()) {
                    case "leak detected":
                        displayText = "🚨 LEAK DETECTED";
                        backgroundColor = Color.RED;
                        notificationTitle = "🚨 CRITICAL: WATER LEAK DETECTED!";
                        notificationText = "Immediate action required! Check water system.";
                        isCritical = true;
                        break;
                    case "flow warning":
                        displayText = "⚠️ FLOW ANOMALY";
                        backgroundColor = Color.RED;
                        notificationTitle = " Anomaly Detected";
                        notificationText = "Unusual flow pattern detected in water system.";
                        isCritical = true;
                        break;
                    case "ultrasonic warning":
                        displayText = "⚠️ LEVEL ANOMALY";
                        backgroundColor = Color.YELLOW;
                        notificationTitle = "⚠️ Tank Level Anomaly";
                        notificationText = "Unusual water tank level detected.";
                        isCritical = true;
                        break;
                    case "no_data":
                        displayText = "📊 WAITING FOR DATA";
                        backgroundColor = Color.GRAY;
                        break;
                    case "error":
                        displayText = "❌ CONNECTION ERROR";
                        backgroundColor = Color.DKGRAY;
                        notificationTitle = "⚠️ System Connection Error";
                        notificationText = "Cannot connect to monitoring system.";
                        break;
                    default: // "normal"
                        displayText = "✓ SYSTEM NORMAL";
                        backgroundColor = Color.GREEN;
                        // Cancel notification if system returns to normal
                        if (showNotification) {
                            notificationManager.cancel(NOTIFICATION_ID);
                        }
                        break;
                }

                tvWarning.setText(displayText);
                warningCard.setCardBackgroundColor(backgroundColor);

                // Set text color based on background
                if (backgroundColor == Color.YELLOW) {
                    tvWarning.setTextColor(Color.BLACK);
                } else {
                    tvWarning.setTextColor(Color.WHITE);
                }

                // Show notification for critical warnings
                if (showNotification && isCritical && !notificationTitle.isEmpty()) {
                    showHighPriorityNotification(notificationTitle, notificationText);
                }
            }
        });
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Leakage Alert Channel";
            String description = "Channel for water leakage alerts";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 250, 500});

            // Register the channel with the system
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private void showHighPriorityNotification(String title, String text) {
        // Create an explicit intent for opening the app when notification is tapped
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_warning) // Add a warning icon to your drawable folder
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(true) // Makes notification sticky (can't be dismissed easily)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true); // Shows even on locked screen for critical alerts

        // Add different styles based on warning type
        if (title.contains("CRITICAL")) {
            builder.setColor(Color.RED)
                    .setLights(Color.RED, 1000, 1000)
                    .setVibrate(new long[]{0, 500, 250, 500, 250, 500});
        } else {
            builder.setColor(Color.YELLOW)
                    .setLights(Color.YELLOW, 500, 1000)
                    .setVibrate(new long[]{0, 250, 250});
        }

        // Show notification
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel notification when app closes (optional)
        // notificationManager.cancel(NOTIFICATION_ID);
    }
}