package com.example.leakagealert;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import com.example.leakagealert.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    TextView tvDistance, tvPercentage, tvLiters;
    DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDistance = findViewById(R.id.tvDistance);
        tvPercentage = findViewById(R.id.tvPercentage);
        tvLiters = findViewById(R.id.tvLiters);

        dbRef = FirebaseDatabase.getInstance()
                .getReference("WaterTank")
                .child("Data");

        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {

                if (snapshot.exists()) {
                    Double distance = snapshot.child("distance_cm").getValue(Double.class);
                    Double percentage = snapshot.child("percentage").getValue(Double.class);
                    Double liters = snapshot.child("liters").getValue(Double.class);

                    if (distance != null)
                        tvDistance.setText("Distance: " + distance + " cm");

                    if (percentage != null)
                        tvPercentage.setText("Tank Level: " + percentage + " %");

                    if (liters != null)
                        tvLiters.setText("Water Lost: " + liters + " L");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                tvDistance.setText("Firebase error");
            }
        });
    }
}
