package com.example.face_recognition_realtime_camerax;

public class Logger {
        public static void log(String message) {
            MainActivity.setMessage( MainActivity.logTextView.getText().toString() + "\n" + ">> " + message );

            // To scroll to the last message
            // See this SO answer -> https://stackoverflow.com/a/37806544/10878733
            while ( MainActivity.logTextView.canScrollVertically(1) ) {
                MainActivity.logTextView.scrollBy(0, 10);
            }
        }
}
