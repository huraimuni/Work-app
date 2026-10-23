package kr.todanjeon.workapp;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/** Firebase(서버) 연결 설정. 이 값들은 공개되어도 되는 식별자다. 접근 통제는 Firestore 보안 규칙이 한다. */
final class Cloud {
    private static final String API_KEY = "AIzaSyDF9qHLsjDmjAWkliYJYssnG_9ujPhasPE";
    private static final String APP_ID = "1:48232341344:android:1f92f90d8572379ba34805";

    static void init(Context c) {
        if (!FirebaseApp.getApps(c).isEmpty()) return;
        FirebaseOptions o = new FirebaseOptions.Builder()
                .setApiKey(API_KEY)
                .setApplicationId(APP_ID)
                .setProjectId("nine-family")
                .setStorageBucket("nine-family.firebasestorage.app")
                .setGcmSenderId("48232341344")
                .build();
        FirebaseApp.initializeApp(c, o);
    }

    static FirebaseFirestore fs() {
        return FirebaseFirestore.getInstance();
    }

    static FirebaseAuth auth() {
        return FirebaseAuth.getInstance();
    }

    /** 010-1234-5678 같은 입력을 +821012345678 형태로 바꾼다. */
    static String e164(String input) {
        String d = input == null ? "" : input.replaceAll("[^0-9+]", "");
        if (d.startsWith("+")) return d;
        if (d.startsWith("0")) return "+82" + d.substring(1);
        if (d.startsWith("82")) return "+" + d;
        return "+82" + d;
    }

    private Cloud() {
    }
}
